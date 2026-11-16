package com.scott.payment.admin.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCardBrandDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelCapabilityCurrencyDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelInfoDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelMidConfigDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.ChannelPaymentCapabilityDO;
import com.scott.payment.admin.entity.channel.ChannelEntities.MerchantChannelMidBindingDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeRuleDO;
import com.scott.payment.admin.mapper.ChannelCapabilityCardBrandMapper;
import com.scott.payment.admin.mapper.ChannelCapabilityCurrencyMapper;
import com.scott.payment.admin.mapper.ChannelInfoMapper;
import com.scott.payment.admin.mapper.ChannelMidConfigMapper;
import com.scott.payment.admin.mapper.ChannelPaymentCapabilityMapper;
import com.scott.payment.admin.mapper.FeeRuleMapper;
import com.scott.payment.admin.mapper.MerchantChannelMidBindingMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 计算商户当前实际可路由支付能力，并校验路由唯一性和生效费率覆盖。
 *
 * <p>本服务复用支付运行时的硬约束口径，但只返回非敏感配置摘要。MID 元数据、渠道密钥和
 * 商户资金信息不会进入开通结果。</p>
 */
@Service
public class MerchantPaymentCapabilityReadinessService {

    private static final long NOT_DELETED = 0L;
    private static final int ENABLED = 1;
    private static final String ALL = "ALL";
    private static final String NONE = "NONE";
    private static final String BANK_CARD = "BANK_CARD";
    private static final String BUSINESS_ACQUIRING = "ACQUIRING";
    private static final Set<String> PLATFORM_TRANSACTION_TYPES = Set.of(
            "PAYMENT", "AUTHORIZATION", "CAPTURE", "REFUND", "VOID", "CHARGEBACK",
            "PRE_AUTHORIZATION", "PRE_AUTH_COMPLETION", "INCREMENTAL_AUTHORIZATION", "REPRESENTMENT");

    private final MerchantChannelMidBindingMapper bindingMapper;
    private final ChannelMidConfigMapper midMapper;
    private final ChannelInfoMapper channelMapper;
    private final ChannelPaymentCapabilityMapper capabilityMapper;
    private final ChannelCapabilityCurrencyMapper currencyMapper;
    private final ChannelCapabilityCardBrandMapper cardBrandMapper;
    private final FeeRuleMapper feeRuleMapper;

    /**
     * 创建商户支付能力就绪检查服务。
     */
    public MerchantPaymentCapabilityReadinessService(MerchantChannelMidBindingMapper bindingMapper,
                                                     ChannelMidConfigMapper midMapper,
                                                     ChannelInfoMapper channelMapper,
                                                     ChannelPaymentCapabilityMapper capabilityMapper,
                                                     ChannelCapabilityCurrencyMapper currencyMapper,
                                                     ChannelCapabilityCardBrandMapper cardBrandMapper,
                                                     FeeRuleMapper feeRuleMapper) {
        this.bindingMapper = bindingMapper;
        this.midMapper = midMapper;
        this.channelMapper = channelMapper;
        this.capabilityMapper = capabilityMapper;
        this.currencyMapper = currencyMapper;
        this.cardBrandMapper = cardBrandMapper;
        this.feeRuleMapper = feeRuleMapper;
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
        Map<Long, ChannelInfoDO> channels = channels(mids.values());
        List<ChannelPaymentCapabilityDO> capabilities = capabilities(channels.keySet());
        Map<Long, List<String>> currencies = currencies(capabilities);
        Map<Long, List<String>> brands = cardBrands(capabilities);
        Map<Long, List<ChannelPaymentCapabilityDO>> capabilitiesByChannel = capabilities.stream()
                .collect(Collectors.groupingBy(ChannelPaymentCapabilityDO::getChannelId));

        int unavailableBindingCount = 0;
        List<RouteCapability> routes = new ArrayList<>();
        for (MerchantChannelMidBindingDO binding : activeBindings) {
            ChannelMidConfigDO mid = mids.get(binding.getMidConfigId());
            ChannelInfoDO channel = mid == null ? null : channels.get(mid.getChannelId());
            if (!available(mid, channel, now)) {
                unavailableBindingCount++;
                continue;
            }
            List<RouteCapability> bindingRoutes = routes(
                    binding, mid, channel,
                    capabilitiesByChannel.getOrDefault(channel.getId(), List.of()), currencies, brands);
            if (bindingRoutes.isEmpty()) {
                unavailableBindingCount++;
            } else {
                routes.addAll(bindingRoutes);
            }
        }

        Map<CapabilityKey, List<RouteCapability>> routesByCapability = routes.stream()
                .collect(Collectors.groupingBy(RouteCapability::key, LinkedHashMap::new, Collectors.toList()));
        List<CapabilityKey> routeConflicts = routesByCapability.entrySet().stream()
                .filter(entry -> hasRouteConflict(entry.getValue()))
                .map(Map.Entry::getKey)
                .sorted(CapabilityKey.ORDER)
                .toList();
        List<FeeRuleDO> feeRules = feePlanVersionId == null ? List.of() : feeRules(feePlanVersionId);
        List<CapabilityKey> feeGaps = new ArrayList<>();
        List<CapabilityKey> feeAmbiguities = new ArrayList<>();
        for (CapabilityKey capability : routesByCapability.keySet().stream().sorted(CapabilityKey.ORDER).toList()) {
            String feeCategory = requiredFeeCategory(capability.transactionType());
            if (feeCategory == null || feePlanVersionId == null) {
                continue;
            }
            long matchingRules = feeRules.stream()
                    .filter(rule -> feeRuleMatches(rule, feeCategory, capability))
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

    /** 查询当前生效的商户 MID 绑定，过期和未来生效记录不参与实际能力计算。 */
    private List<MerchantChannelMidBindingDO> activeBindings(String merchantId, LocalDateTime now) {
        return safe(bindingMapper.selectList(Wrappers.<MerchantChannelMidBindingDO>lambdaQuery()
                .eq(MerchantChannelMidBindingDO::getMerchantId, merchantId)
                .eq(MerchantChannelMidBindingDO::getBindingStatus, ENABLED)
                .eq(MerchantChannelMidBindingDO::getDeleted, NOT_DELETED)
                .and(value -> value.isNull(MerchantChannelMidBindingDO::getEffectiveTime)
                        .or().le(MerchantChannelMidBindingDO::getEffectiveTime, now))
                .and(value -> value.isNull(MerchantChannelMidBindingDO::getExpireTime)
                        .or().gt(MerchantChannelMidBindingDO::getExpireTime, now))
                .orderByAsc(MerchantChannelMidBindingDO::getId)));
    }

    /** 批量读取绑定引用的 MID，保留不可用行供门禁给出明确问题。 */
    private Map<Long, ChannelMidConfigDO> mids(List<MerchantChannelMidBindingDO> bindings) {
        Set<Long> ids = ids(bindings.stream().map(MerchantChannelMidBindingDO::getMidConfigId).toList());
        if (ids.isEmpty()) return Map.of();
        return index(midMapper.selectList(Wrappers.<ChannelMidConfigDO>lambdaQuery()
                .in(ChannelMidConfigDO::getId, ids)
                .eq(ChannelMidConfigDO::getDeleted, NOT_DELETED)), ChannelMidConfigDO::getId);
    }

    /** 批量读取 MID 引用的渠道，停用渠道仍由 available 统一判定。 */
    private Map<Long, ChannelInfoDO> channels(Collection<ChannelMidConfigDO> mids) {
        Set<Long> ids = ids(mids.stream().map(ChannelMidConfigDO::getChannelId).toList());
        if (ids.isEmpty()) return Map.of();
        return index(channelMapper.selectList(Wrappers.<ChannelInfoDO>lambdaQuery()
                .in(ChannelInfoDO::getId, ids)
                .eq(ChannelInfoDO::getDeleted, NOT_DELETED)), ChannelInfoDO::getId);
    }

    /** 批量读取启用的收单能力，避免按 MID 产生 N+1 查询。 */
    private List<ChannelPaymentCapabilityDO> capabilities(Set<Long> channelIds) {
        if (channelIds.isEmpty()) return List.of();
        return safe(capabilityMapper.selectList(Wrappers.<ChannelPaymentCapabilityDO>lambdaQuery()
                .in(ChannelPaymentCapabilityDO::getChannelId, channelIds)
                .eq(ChannelPaymentCapabilityDO::getBusinessType, BUSINESS_ACQUIRING)
                .eq(ChannelPaymentCapabilityDO::getCapabilityStatus, ENABLED)
                .eq(ChannelPaymentCapabilityDO::getDeleted, NOT_DELETED)
                .orderByAsc(ChannelPaymentCapabilityDO::getSortOrder)
                .orderByAsc(ChannelPaymentCapabilityDO::getId)));
    }

    /** 按能力批量读取启用币种。 */
    private Map<Long, List<String>> currencies(List<ChannelPaymentCapabilityDO> capabilities) {
        Set<Long> ids = ids(capabilities.stream().map(ChannelPaymentCapabilityDO::getId).toList());
        if (ids.isEmpty()) return Map.of();
        return safe(currencyMapper.selectList(Wrappers.<ChannelCapabilityCurrencyDO>lambdaQuery()
                .in(ChannelCapabilityCurrencyDO::getCapabilityId, ids)
                .eq(ChannelCapabilityCurrencyDO::getCurrencyStatus, ENABLED)
                .eq(ChannelCapabilityCurrencyDO::getDeleted, NOT_DELETED)))
                .stream().filter(row -> StringUtils.hasText(row.getCurrencyCode()))
                .collect(Collectors.groupingBy(ChannelCapabilityCurrencyDO::getCapabilityId,
                        Collectors.mapping(row -> upper(row.getCurrencyCode()), Collectors.toList())));
    }

    /** 按能力批量读取启用卡品牌；非银行卡能力不依赖该表。 */
    private Map<Long, List<String>> cardBrands(List<ChannelPaymentCapabilityDO> capabilities) {
        Set<Long> ids = ids(capabilities.stream().map(ChannelPaymentCapabilityDO::getId).toList());
        if (ids.isEmpty()) return Map.of();
        return safe(cardBrandMapper.selectList(Wrappers.<ChannelCapabilityCardBrandDO>lambdaQuery()
                .in(ChannelCapabilityCardBrandDO::getCapabilityId, ids)
                .eq(ChannelCapabilityCardBrandDO::getBrandStatus, ENABLED)
                .eq(ChannelCapabilityCardBrandDO::getDeleted, NOT_DELETED)))
                .stream().filter(row -> StringUtils.hasText(row.getCardBrand()))
                .collect(Collectors.groupingBy(ChannelCapabilityCardBrandDO::getCapabilityId,
                        Collectors.mapping(row -> upper(row.getCardBrand()), Collectors.toList())));
    }

    /** 读取当前不可变版本的原子费用规则。 */
    private List<FeeRuleDO> feeRules(Long feePlanVersionId) {
        return safe(feeRuleMapper.selectList(Wrappers.<FeeRuleDO>lambdaQuery()
                .eq(FeeRuleDO::getPlanVersionId, feePlanVersionId)
                .eq(FeeRuleDO::getDeleted, NOT_DELETED)
                .orderByAsc(FeeRuleDO::getSortNo)
                .orderByAsc(FeeRuleDO::getId)));
    }

    /** 判断 MID、渠道和有效期是否满足支付运行时的基础硬约束。 */
    private boolean available(ChannelMidConfigDO mid, ChannelInfoDO channel, LocalDateTime now) {
        return mid != null && channel != null
                && Integer.valueOf(ENABLED).equals(mid.getMidStatus())
                && active(now, mid.getEffectiveTime(), mid.getExpireTime())
                && BUSINESS_ACQUIRING.equals(upper(mid.getBusinessType()))
                && Integer.valueOf(ENABLED).equals(channel.getChannelStatus())
                && Integer.valueOf(ENABLED).equals(channel.getSupportAcquiring());
    }

    /** 展开单个有效绑定的实际交易类型、支付方式/卡品牌和币种交集。 */
    private List<RouteCapability> routes(MerchantChannelMidBindingDO binding,
                                         ChannelMidConfigDO mid,
                                         ChannelInfoDO channel,
                                         List<ChannelPaymentCapabilityDO> capabilities,
                                         Map<Long, List<String>> currencies,
                                         Map<Long, List<String>> brands) {
        List<RouteCapability> routes = new ArrayList<>();
        for (ChannelPaymentCapabilityDO capability : capabilities) {
            String paymentType = upper(capability.getPaymentMethod());
            if (!scopeAllows(mid.getPaymentMethodScope(), paymentType)) continue;
            Set<String> transactionTypes = scopeIntersection(
                    mid.getTransactionTypeScope(), capability.getTransactionType(), PLATFORM_TRANSACTION_TYPES);
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
    private boolean feeRuleMatches(FeeRuleDO rule, String feeCategory, CapabilityKey capability) {
        return feeCategory.equals(upper(rule.getFeeCategory()))
                && NONE.equals(upper(rule.getRiskServiceType()))
                && dimensionMatches(rule.getTransactionType(), capability.transactionType())
                && dimensionMatches(rule.getPaymentType(), capability.paymentType())
                && dimensionMatches(rule.getPaymentMethod(), capability.paymentMethod());
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

    private <T> List<T> safe(List<T> rows) {
        return rows == null ? List.of() : rows;
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
