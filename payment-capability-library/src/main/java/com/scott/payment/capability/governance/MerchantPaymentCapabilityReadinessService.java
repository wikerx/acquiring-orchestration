package com.scott.payment.capability.governance;

import com.scott.payment.capability.governance.MerchantPaymentReadinessFacts.*;
import org.springframework.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * 计算商户当前实际可路由支付能力，并校验路由唯一性和生效费率覆盖。
 *
 * <p>本服务复用支付运行时的硬约束口径，但只返回非敏感配置摘要。MID 元数据、渠道密钥和
 * 商户资金信息不会进入开通结果。</p>
 */
@Slf4j
public class MerchantPaymentCapabilityReadinessService {

    private static final int ENABLED = 1;
    private static final String ALL = "ALL";
    private static final String NONE = "NONE";
    private static final String BANK_CARD = "BANK_CARD";
    private static final String BUSINESS_ACQUIRING = "ACQUIRING";
    private static final String BUSINESS_PAYOUT = "PAYOUT";
    private static final Set<String> PAYOUT_TRANSACTION_TYPES = Set.of("PAYOUT", "CANCEL");
    private static final Set<String> INITIAL_FEE_ACTIONS = Set.of("PAYMENT", "AUTHORIZATION", "PRE_AUTHORIZATION");
    private static final Set<String> PLATFORM_TRANSACTION_TYPES = Set.of(
            "PAYMENT", "AUTHORIZATION", "CAPTURE", "REFUND", "VOID", "CHARGEBACK",
            "PRE_AUTHORIZATION", "PRE_AUTH_COMPLETION", "INCREMENTAL_AUTHORIZATION", "REPRESENTMENT");

    private final MerchantPaymentReadinessFacts facts;

    public MerchantPaymentCapabilityReadinessService(MerchantPaymentReadinessFacts facts) {
        this.facts = facts;
    }

    /**
     * 按当前时点评估商户收单能力；费率版本为空时只执行渠道与路由检查。
     *
     * @param merchantId 平台商户号
     * @param feePlanVersionId 当前生效商户费率版本，可空
     * @param now 门禁评估时间
     * @return 可用于开通门禁和工作台展示的能力结果
     */
    public ReadinessResult evaluate(String merchantId, Long feePlanVersionId, LocalDateTime now) {
        if (!StringUtils.hasText(merchantId) || now == null) {
            throw new IllegalArgumentException("merchant and evaluation time are required");
        }
        List<MerchantChannelMidBindingDO> activeBindings = activeBindings(merchantId.trim(), now);
        if (activeBindings.isEmpty()) {
            return ReadinessResult.empty(List.of("CHANNEL_MID_NOT_ACTIVE"));
        }

        Map<Long, ChannelMidConfigDO> mids = mids(activeBindings);
        return evaluateBindings(activeBindings, mids, feePlanVersionId, now, BUSINESS_ACQUIRING, false, facts::feeRules);
    }

    /** 为商户详情按 MID 归属分组，保留一组不可用时另一组的评估结果。 */
    public CurrentReadinessResult evaluateCurrent(String merchantId, LocalDateTime now,
                                                   Supplier<Long> activeFeeVersion) {
        return evaluateCurrent(merchantId, now, activeFeeVersion, facts::feeRules);
    }

    CurrentReadinessResult evaluateCurrent(String merchantId, LocalDateTime now, Supplier<Long> activeFeeVersion,
                                           Function<Long, List<FeeRuleDO>> loadRules) {
        List<MerchantChannelMidBindingDO> bindings;
        Map<Long, ChannelMidConfigDO> mids;
        try {
            bindings = activeBindings(merchantId, now);
            mids = mids(bindings);
        } catch (RuntimeException exception) {
            log.warn("商户详情支付能力基础数据不可用, event: MERCHANT_PAYMENT_READINESS_FACTS_UNAVAILABLE, merchantId: {}, errorType: {}",
                    merchantId, exception.getClass().getSimpleName());
            return new CurrentReadinessResult(null, null);
        }
        List<MerchantChannelMidBindingDO> acquiring = new ArrayList<>();
        List<MerchantChannelMidBindingDO> payout = new ArrayList<>();
        for (MerchantChannelMidBindingDO binding : bindings) {
            ChannelMidConfigDO mid = binding.getMidConfigId() == null ? null : mids.get(binding.getMidConfigId());
            String businessType = mid == null ? "" : upper(mid.getBusinessType());
            if (BUSINESS_ACQUIRING.equals(businessType)) {
                acquiring.add(binding);
            } else if (BUSINESS_PAYOUT.equals(businessType)) {
                payout.add(binding);
            } else {
                log.warn("商户详情绑定缺少有效业务归属, event: MERCHANT_PAYMENT_BINDING_BUSINESS_TYPE_MISSING, merchantId: {}, bindingId: {}",
                        merchantId, binding.getId());
                return new CurrentReadinessResult(null, null);
            }
        }
        return new CurrentReadinessResult(
                evaluateCurrentBusiness(merchantId, acquiring, mids, now, BUSINESS_ACQUIRING, activeFeeVersion, loadRules),
                evaluateCurrentBusiness(merchantId, payout, mids, now, BUSINESS_PAYOUT, () -> null, loadRules));
    }

    private ReadinessResult evaluateCurrentBusiness(String merchantId, List<MerchantChannelMidBindingDO> bindings,
                                                    Map<Long, ChannelMidConfigDO> mids, LocalDateTime now,
                                                    String businessType, Supplier<Long> activeFeeVersion,
                                                    Function<Long, List<FeeRuleDO>> loadRules) {
        if (bindings.isEmpty()) return ReadinessResult.empty(List.of());
        try {
            Map<Long, ChannelMidConfigDO> businessMids = bindings.stream()
                    .map(binding -> mids.get(binding.getMidConfigId()))
                    .collect(Collectors.toMap(ChannelMidConfigDO::getId, Function.identity(), (left, right) -> left));
            return evaluateBindings(bindings, businessMids, activeFeeVersion.get(), now, businessType, true, loadRules);
        } catch (RuntimeException exception) {
            log.warn("商户详情业务能力暂不可用, event: MERCHANT_PAYMENT_BUSINESS_READINESS_UNAVAILABLE, merchantId: {}, businessType: {}, errorType: {}",
                    merchantId, businessType, exception.getClass().getSimpleName());
            return null;
        }
    }

    private ReadinessResult evaluateBindings(List<MerchantChannelMidBindingDO> activeBindings,
                                             Map<Long, ChannelMidConfigDO> mids, Long feePlanVersionId,
                                             LocalDateTime now, String businessType, boolean currentDetail,
                                             Function<Long, List<FeeRuleDO>> loadRules) {
        boolean acquiring = BUSINESS_ACQUIRING.equals(businessType);
        Map<Long, ChannelInfoDO> channels = channels(mids.values());
        List<ChannelPaymentCapabilityDO> capabilities = capabilities(channels.keySet(), businessType);
        Map<Long, List<String>> currencies = currencies(capabilities);
        Map<Long, List<String>> brands = cardBrands(capabilities);
        Map<Long, List<ChannelPaymentCapabilityDO>> capabilitiesByChannel = capabilities.stream()
                .collect(Collectors.groupingBy(ChannelPaymentCapabilityDO::getChannelId));

        int unavailableBindingCount = 0;
        List<RouteCapability> routes = new ArrayList<>();
        for (MerchantChannelMidBindingDO binding : activeBindings) {
            ChannelMidConfigDO mid = mids.get(binding.getMidConfigId());
            ChannelInfoDO channel = mid == null ? null : channels.get(mid.getChannelId());
            if (!available(mid, channel, now, businessType)) {
                unavailableBindingCount++;
                continue;
            }
            List<RouteCapability> bindingRoutes = routes(
                    binding, mid, channel,
                    capabilitiesByChannel.getOrDefault(channel.getId(), List.of()), currencies, brands, acquiring);
            if (bindingRoutes.isEmpty()) {
                unavailableBindingCount++;
            } else {
                routes.addAll(bindingRoutes);
            }
        }

        Map<CapabilityKey, List<RouteCapability>> routesByCapability = routes.stream()
                .collect(Collectors.groupingBy(RouteCapability::key, LinkedHashMap::new, Collectors.toList()));
        List<CapabilityKey> routeConflicts = acquiring ? routesByCapability.entrySet().stream()
                .filter(entry -> hasRouteConflict(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted(CapabilityKey.ORDER)
                .toList() : List.of();
        List<FeeRuleDO> feeRules = feePlanVersionId == null ? List.of() : loadRules.apply(feePlanVersionId);
        List<CapabilityKey> feeGaps = new ArrayList<>();
        List<CapabilityKey> feeAmbiguities = new ArrayList<>();
        for (CapabilityKey capability : routesByCapability.keySet().stream().sorted(CapabilityKey.ORDER).toList()) {
            String feeCategory = requiredFeeCategory(capability.transactionType());
            if (!acquiring || feeCategory == null || (feePlanVersionId == null && !currentDetail)) {
                continue;
            }
            long matchingRules = feeRules.stream()
                    .filter(rule -> feeRuleMatches(rule, feeCategory, capability.transactionType(),
                            capability.paymentType(), capability.paymentMethod()))
                    .count();
            if (matchingRules == 0) {
                feeGaps.add(capability);
            } else if (matchingRules > 1) {
                feeAmbiguities.add(capability);
            }
        }

        LinkedHashSet<String> issues = new LinkedHashSet<>();
        if (unavailableBindingCount > 0) issues.add("CHANNEL_MID_UNAVAILABLE");
        if (routesByCapability.isEmpty()) issues.add("CHANNEL_PAYMENT_CAPABILITY_EMPTY");
        if (!routeConflicts.isEmpty()) issues.add("CHANNEL_ROUTE_CONFLICT");
        if (!feeGaps.isEmpty()) issues.add("FEE_CAPABILITY_NOT_COVERED");
        if (!feeAmbiguities.isEmpty()) issues.add("FEE_CAPABILITY_AMBIGUOUS");
        return new ReadinessResult(
                activeBindings.size(), unavailableBindingCount,
                routesByCapability.keySet().stream().sorted(CapabilityKey.ORDER).toList(),
                routeConflicts, feeGaps, feeAmbiguities,
                routes.stream().map(route -> new RoutedCapability(
                                route.channelId(), route.channelCode(), route.key()))
                        .distinct()
                        .sorted(RoutedCapability.ORDER)
                        .toList(),
                List.copyOf(issues));
    }

    private List<MerchantChannelMidBindingDO> activeBindings(String merchantId, LocalDateTime now) {
        return facts.activeBindings(merchantId, now);
    }

    private Map<Long, ChannelMidConfigDO> mids(List<MerchantChannelMidBindingDO> bindings) {
        Set<Long> ids = ids(bindings.stream().map(MerchantChannelMidBindingDO::getMidConfigId).toList());
        return ids.isEmpty() ? Map.of() : index(facts.mids(ids), ChannelMidConfigDO::getId);
    }

    private Map<Long, ChannelInfoDO> channels(Collection<ChannelMidConfigDO> mids) {
        Set<Long> ids = ids(mids.stream().map(ChannelMidConfigDO::getChannelId).toList());
        return ids.isEmpty() ? Map.of() : index(facts.channels(ids), ChannelInfoDO::getId);
    }

    private List<ChannelPaymentCapabilityDO> capabilities(Set<Long> channelIds, String businessType) {
        return channelIds.isEmpty() ? List.of() : facts.capabilities(channelIds, businessType);
    }

    private Map<Long, List<String>> currencies(List<ChannelPaymentCapabilityDO> capabilities) {
        Set<Long> ids = ids(capabilities.stream().map(ChannelPaymentCapabilityDO::getId).toList());
        return ids.isEmpty() ? Map.of() : facts.currencies(ids).stream()
                .filter(row -> StringUtils.hasText(row.getCurrencyCode()))
                .collect(Collectors.groupingBy(ChannelCapabilityCurrencyDO::getCapabilityId,
                        Collectors.mapping(row -> upper(row.getCurrencyCode()), Collectors.toList())));
    }

    private Map<Long, List<String>> cardBrands(List<ChannelPaymentCapabilityDO> capabilities) {
        Set<Long> ids = ids(capabilities.stream().map(ChannelPaymentCapabilityDO::getId).toList());
        return ids.isEmpty() ? Map.of() : facts.cardBrands(ids).stream()
                .filter(row -> StringUtils.hasText(row.getCardBrand()))
                .collect(Collectors.groupingBy(ChannelCapabilityCardBrandDO::getCapabilityId,
                        Collectors.mapping(row -> upper(row.getCardBrand()), Collectors.toList())));
    }

    /** 当前费率版本对支付方式的静态覆盖，可独立于实际 MID 和路由检查。 */
    public BiPredicate<String, String> initialFeeCoverage(long feePlanVersionId) {
        return initialFeeCoverage(facts.feeRules(feePlanVersionId));
    }

    BiPredicate<String, String> initialFeeCoverage(List<FeeRuleDO> rules) {
        return (paymentType, paymentMethod) -> rules.stream().anyMatch(rule -> INITIAL_FEE_ACTIONS.stream()
                .anyMatch(action -> feeRuleMatches(rule, "TRANSACTION_FEE", action, paymentType, paymentMethod)));
    }

    /** 判断 MID、渠道和有效期是否满足支付运行时的基础硬约束。 */
    private boolean available(ChannelMidConfigDO mid, ChannelInfoDO channel, LocalDateTime now, String businessType) {
        return mid != null && channel != null
                && Integer.valueOf(ENABLED).equals(mid.getMidStatus())
                && active(now, mid.getEffectiveTime(), mid.getExpireTime())
                && businessType.equals(upper(mid.getBusinessType()))
                && Integer.valueOf(ENABLED).equals(channel.getChannelStatus())
                && Integer.valueOf(ENABLED).equals(BUSINESS_ACQUIRING.equals(businessType)
                        ? channel.getSupportAcquiring() : channel.getSupportPayout());
    }

    /** 展开单个有效绑定的实际交易类型、支付方式/卡品牌和币种交集。 */
    private List<RouteCapability> routes(MerchantChannelMidBindingDO binding,
                                         ChannelMidConfigDO mid,
                                         ChannelInfoDO channel,
                                         List<ChannelPaymentCapabilityDO> capabilities,
                                         Map<Long, List<String>> currencies,
                                         Map<Long, List<String>> brands,
                                         boolean acquiring) {
        List<RouteCapability> routes = new ArrayList<>();
        for (ChannelPaymentCapabilityDO capability : capabilities) {
            String paymentType = upper(capability.getPaymentMethod());
            if (!scopeAllows(mid.getPaymentMethodScope(), paymentType)) continue;
            Set<String> transactionTypes = scopeIntersection(mid.getTransactionTypeScope(),
                    capability.getTransactionType(), acquiring ? PLATFORM_TRANSACTION_TYPES : PAYOUT_TRANSACTION_TYPES);
            if (!acquiring) transactionTypes.retainAll(PAYOUT_TRANSACTION_TYPES);
            if (!Integer.valueOf(ENABLED).equals(capability.getSupportIncrementalAuthorization())) {
                transactionTypes.remove("INCREMENTAL_AUTHORIZATION");
            }
            Set<String> routeCurrencies = allowedValues(currencies.get(capability.getId()), mid.getCurrencyScope());
            Set<String> paymentMethods = BANK_CARD.equals(paymentType)
                    ? allowedValues(brands.get(capability.getId()), mid.getCardBrandScope())
                    : Set.of(ALL);
            for (String transactionType : transactionTypes) {
                for (String paymentMethod : paymentMethods) {
                    for (String currency : routeCurrencies) {
                        CapabilityKey key = new CapabilityKey(transactionType, paymentType, paymentMethod, currency);
                        routes.add(new RouteCapability(
                                key, binding.getId(), mid.getId(), channel.getId(), channel.getChannelCode(),
                                capability.getId(), capability.getSortOrder()));
                    }
                }
            }
        }
        return routes;
    }

    /** 同一能力的最小非空优先级必须唯一，否则运行时会拒绝路由。 */
    private boolean hasRouteConflict(List<RouteCapability> routes) {
        if (routes.size() <= 1) return false;
        Integer minimum = routes.stream().map(RouteCapability::sortOrder)
                .filter(java.util.Objects::nonNull).min(Integer::compareTo).orElse(null);
        if (minimum == null) return true;
        return routes.stream().filter(route -> minimum.equals(route.sortOrder())).count() != 1;
    }

    /** 根据交易动作确定必须覆盖的主费用类别；后续动作沿用原交易费用，不重复要求。 */
    private String requiredFeeCategory(String transactionType) {
        return switch (transactionType) {
            case "PAYMENT", "AUTHORIZATION", "PRE_AUTHORIZATION" -> "TRANSACTION_FEE";
            case "REFUND" -> "REFUND_FEE";
            case "CHARGEBACK", "REPRESENTMENT" -> "DISPUTE_FEE";
            default -> null;
        };
    }

    /** 使用清分维度的 ALL/精确匹配语义判断一条原子费率能否覆盖实际能力。 */
    private boolean feeRuleMatches(FeeRuleDO rule, String feeCategory, String transactionType,
                                   String paymentType, String paymentMethod) {
        return feeCategory.equals(upper(rule.getFeeCategory()))
                && NONE.equals(upper(rule.getRiskServiceType()))
                && dimensionMatches(rule.getTransactionType(), transactionType)
                && dimensionMatches(rule.getPaymentType(), paymentType)
                && dimensionMatches(rule.getPaymentMethod(), paymentMethod);
    }

    private boolean dimensionMatches(String configured, String actual) {
        String normalized = upper(configured);
        return ALL.equals(normalized) || normalized.equals(actual);
    }

    private boolean scopeAllows(String scope, String value) {
        return scopeValues(scope, Set.of(value)).contains(value);
    }

    private Set<String> scopeIntersection(String left, String right, Set<String> universe) {
        LinkedHashSet<String> values = new LinkedHashSet<>(scopeValues(left, universe));
        values.retainAll(scopeValues(right, universe));
        return values;
    }

    private Set<String> allowedValues(List<String> values, String scope) {
        if (values == null || values.isEmpty()) return Set.of();
        LinkedHashSet<String> normalized = values.stream().filter(StringUtils::hasText)
                .map(this::upper).collect(Collectors.toCollection(LinkedHashSet::new));
        if (!StringUtils.hasText(scope) || ALL.equalsIgnoreCase(scope.trim())) return normalized;
        if (NONE.equalsIgnoreCase(scope.trim())) return Set.of();
        normalized.retainAll(scopeValues(scope, Set.of()));
        return normalized;
    }

    private Set<String> scopeValues(String scope, Set<String> universe) {
        if (!StringUtils.hasText(scope) || ALL.equalsIgnoreCase(scope.trim())) {
            return new LinkedHashSet<>(universe);
        }
        return java.util.Arrays.stream(scope.split(","))
                .filter(StringUtils::hasText).map(this::upper)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean active(LocalDateTime now, LocalDateTime effectiveTime, LocalDateTime expireTime) {
        return (effectiveTime == null || !now.isBefore(effectiveTime))
                && (expireTime == null || now.isBefore(expireTime));
    }

    private <T> Map<Long, T> index(List<T> rows, Function<T, Long> idExtractor) {
        if (rows == null) return Map.of();
        return rows.stream().filter(row -> idExtractor.apply(row) != null)
                .collect(Collectors.toMap(idExtractor, Function.identity(), (left, right) -> left));
    }

    private Set<Long> ids(Collection<Long> values) {
        return values.stream().filter(java.util.Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    /** 商户可使用的单个原子支付能力。 */
    public record CapabilityKey(String transactionType,
                                String paymentType,
                                String paymentMethod,
                                String currency) {
        private static final Comparator<CapabilityKey> ORDER = Comparator
                .comparing(CapabilityKey::transactionType)
                .thenComparing(CapabilityKey::paymentType)
                .thenComparing(CapabilityKey::paymentMethod)
                .thenComparing(CapabilityKey::currency);
    }

    /**
     * 风控和渠道限额检查使用的内部路由维度；对外 DTO 不暴露渠道或 MID 信息。
     */
    public record RoutedCapability(Long channelId,
                                   String channelCode,
                                   CapabilityKey capability) {
        private static final Comparator<RoutedCapability> ORDER = Comparator
                .comparing(RoutedCapability::channelCode, Comparator.nullsLast(String::compareTo))
                .thenComparing(RoutedCapability::capability, CapabilityKey.ORDER);
    }

    /** 单个能力对应的非敏感路由候选。 */
    private record RouteCapability(CapabilityKey key,
                                   Long bindingId,
                                   Long midConfigId,
                                   Long channelId,
                                   String channelCode,
                                   Long capabilityId,
                                   Integer sortOrder) {
    }

    /** 两个业务的独立评估结果；空表示对应结果无法核实。 */
    public record CurrentReadinessResult(ReadinessResult acquiring, ReadinessResult payout) {
    }

    /** 支付能力开通检查结果；列表保持稳定顺序，便于后续工作台展示和回归断言。 */
    public record ReadinessResult(int activeBindingCount,
                                  int unavailableBindingCount,
                                  List<CapabilityKey> capabilities,
                                  List<CapabilityKey> routeConflicts,
                                  List<CapabilityKey> feeGaps,
                                  List<CapabilityKey> feeAmbiguities,
                                  List<RoutedCapability> routedCapabilities,
                                  List<String> issueCodes) {
        private static ReadinessResult empty(List<String> issues) {
            return new ReadinessResult(0, 0, List.of(), List.of(), List.of(), List.of(), List.of(), issues);
        }
    }
}
