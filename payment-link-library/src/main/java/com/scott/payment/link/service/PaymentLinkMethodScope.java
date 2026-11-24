package com.scott.payment.link.service;

import com.scott.payment.capability.MerchantAdmissionPolicy;
import com.scott.payment.capability.MerchantAdmissionReader;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.db.route.model.MerchantRouteProfile;
import com.scott.payment.link.model.PaymentLinkManagementModels.AllowedMethod;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 按明确的规范方式范围冻结新链接，并在后续会话中复核同一范围。 */
@Service
public class PaymentLinkMethodScope {

    private final PaymentLinkDatabaseCapabilities capabilities;
    private final MerchantAdmissionReader authorityReader;

    public PaymentLinkMethodScope(PaymentLinkDatabaseCapabilities capabilities, MerchantAdmissionReader authorityReader) {
        this.capabilities = capabilities;
        this.authorityReader = authorityReader;
    }

    /** 使用真实授权、主费率及主库有效候选验证范围，不因钱包组件未接好而拒绝创建。 */
    public List<AllowedMethod> resolve(String merchantId, String currency, List<String> keys, boolean requireAll) {
        return resolve(snapshot(merchantId), currency, keys, requireAll);
    }

    /**
     * 从复用快照选出通过目录、授权和费用检查的非卡方式，由 Payment 按订单实际条件检查路由。
     */
    public List<String> hostedNonCardMethods(Snapshot facts, Set<String> frozenMethods) {
        return facts.authority().items().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith("METHOD:") && MerchantAdmissionPolicy.isItemKey(entry.getKey()))
                .filter(entry -> facts.directory().contains(entry.getKey()))
                .filter(entry -> permitsHostedDisplay(facts, entry.getValue()))
                .map(entry -> entry.getKey().substring("METHOD:".length()))
                .filter(method -> frozenMethods == null || frozenMethods.contains(method))
                .filter(method -> facts.fees().stream().filter(fee -> fee.covers("PAYMENT", method, "ALL"))
                        .limit(2).count() == 1)
                .sorted()
                .toList();
    }

    private boolean permitsHostedDisplay(Snapshot facts, MerchantAdmissionReader.ItemAuthority item) {
        if (facts.authority().mode() == null && item.grant() != null
                && "APPLICATION".equals(item.grant().source())) {
            // 未迁移商户可展示已正式审批的非卡选项，实际交易仍需经过原有准入门禁。
            return MerchantAdmissionPolicy.state(item.grant(), facts.evaluatedAt()) == MerchantAdmissionPolicy.State.ACTIVE;
        }
        return MerchantAdmissionPolicy.permits(
                facts.authority().mode(), item.grant(), item.frozenLegacyScope(), facts.evaluatedAt());
    }

    /** 创建预览按一次主库取数绘制各币种，不按方式或币种反复查询数据库。 */
    public Snapshot snapshot(String merchantId) {
        return new Snapshot(authorityReader.read(merchantId), authorityReader.transactionFeeCoverage(merchantId),
                authorityReader.enabledDirectory(), capabilities.profile(merchantId), LocalDateTime.now());
    }

    /**
     * 从支付链接的允许范围提取启用的标准支付方式键。
     */
    public List<String> enabledKeys(Snapshot snapshot, String currency) {
        var keys = snapshot.authority().items().keySet().stream().sorted().toList();
        if (keys.isEmpty()) {
            return List.of();
        }
        return resolve(snapshot, currency, keys, false).stream().flatMap(option -> {
            if ("BANK_CARD".equals(option.getPaymentMethod())) {
                return option.getBrands().stream().map(brand -> "CARD_BRAND:" + brand);
            }
            return java.util.stream.Stream.of("METHOD:" + option.getPaymentMethod());
        }).sorted().toList();
    }

    private List<AllowedMethod> resolve(Snapshot snapshot, String currency, List<String> keys, boolean requireAll) {
        if (keys == null || keys.isEmpty() || keys.stream().anyMatch(key -> !MerchantAdmissionPolicy.isItemKey(key))) {
            throw new ServiceException(ApiResultEnum.PARAM_INVALID);
        }
        LocalDateTime now = snapshot.evaluatedAt();
        var authority = snapshot.authority();
        var fees = snapshot.fees();
        List<AllowedMethod> result = new ArrayList<>();
        for (String key : new LinkedHashSet<>(keys)) {
            var fact = authority.items().get(key);
            boolean allowed = snapshot.directory().contains(key) && fact != null && MerchantAdmissionPolicy.permits(
                    authority.mode(), fact.grant(), fact.frozenLegacyScope(), now);
            boolean card = key.startsWith("CARD_BRAND:");
            String code = key.substring(key.indexOf(':') + 1);
            String method = card ? "BANK_CARD" : code;
            if (!allowed || fees.stream().noneMatch(fee -> fee.covers("PAYMENT", method, card ? code : "ALL"))) {
                if (requireAll) {
                    throw unavailable();
                }
                continue;
            }
            AllowedMethod requested = new AllowedMethod();
            requested.setPaymentMethod(method);
            requested.setBrands(card ? List.of(code) : List.of());
            AllowedMethod candidate = selectCandidate(snapshot.profile(), requested, currency, now);
            if (candidate == null) {
                if (requireAll) {
                    throw unavailable();
                }
                continue;
            }
            // 渠道按付款请求重新选择。这里仅保存方式和品牌，不能把配置投影变成渠道指定参数。
            requested.setThreeDsMode(candidate.getThreeDsMode());
            result.add(requested);
        }
        return List.copyOf(result);
    }

    /** 链接尚未知道付款人地区。初筛只接受当前币种有明确优先目标的配置。 */
    private AllowedMethod selectCandidate(MerchantRouteProfile profile, AllowedMethod requested, String currency,
                                          LocalDateTime now) {
        var resolver = new PaymentLinkCapabilityResolver();
        Map<RouteIdentity, AllowedMethod> candidates = new LinkedHashMap<>();
        for (var option : profile.getRouteOptions()) {
            MerchantRouteProfile single = new MerchantRouteProfile();
            single.setRouteOptions(List.of(option));
            List<AllowedMethod> eligible = resolver.resolve(single, List.of(requested), currency, now);
            if (!eligible.isEmpty()) {
                candidates.putIfAbsent(new RouteIdentity(option.getMidConfigId(), option.getCapabilityId(),
                        option.getCapabilitySortOrder()), eligible.get(0));
            }
        }
        if (candidates.size() == 1) {
            return candidates.values().iterator().next();
        }
        Integer priority = candidates.keySet().stream().map(RouteIdentity::priority)
                .filter(Objects::nonNull).min(Integer::compareTo).orElse(null);
        if (priority == null) {
            return null;
        }
        List<AllowedMethod> first = candidates.entrySet().stream()
                .filter(entry -> priority.equals(entry.getKey().priority())).map(Map.Entry::getValue).toList();
        return first.size() == 1 ? first.get(0) : null;
    }

    private record RouteIdentity(Long midId, Long capabilityId, Integer priority) {
    }

    /** 一次取数的授权、费用、目录和当前路由候选，用途限链接配置投影。 */
    public record Snapshot(MerchantAdmissionReader.MerchantAuthority authority,
                           List<MerchantAdmissionReader.FeeCoverage> fees, Set<String> directory,
                           MerchantRouteProfile profile, LocalDateTime evaluatedAt) {
    }

    /** 只存版本和方式键。后续换 MID 不改变创建时的授权边界。 */
    public static String serialize(List<String> keys) {
        return JsonUtils.toJsonString(new FrozenScope(1, List.copyOf(keys)));
    }

    /** 已持久化的语义未知时拒绝降级到所有品牌。 */
    public static List<String> deserialize(String json) {
        FrozenScope scope = JsonUtils.parseObject(json, FrozenScope.class);
        if (scope == null || scope.version() != 1 || scope.itemKeys() == null || scope.itemKeys().isEmpty()
                || scope.itemKeys().stream().anyMatch(key -> !MerchantAdmissionPolicy.isItemKey(key))) {
            throw unavailable();
        }
        return scope.itemKeys();
    }

    private static ServiceException unavailable() {
        return new ServiceException(ApiResultEnum.MERCHANT_PAYMENT_CONFIG_UNAVAILABLE);
    }

    /** 链接冻结范围的持久化契约。 */
    public record FrozenScope(int version, List<String> itemKeys) {
    }
}
