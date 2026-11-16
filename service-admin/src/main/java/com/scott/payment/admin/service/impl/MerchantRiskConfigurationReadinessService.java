package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.mapper.MerchantActivationReadinessMapper;
import com.scott.payment.admin.mapper.MerchantActivationReadinessMapper.ChannelLimitRow;
import com.scott.payment.admin.mapper.MerchantActivationReadinessMapper.MerchantLimitRow;
import com.scott.payment.admin.mapper.MerchantActivationReadinessMapper.ThreeDsRuleRow;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 校验商户实际支付能力对应的商户限额、渠道限额和银行卡 3DS 兜底策略。
 *
 * <p>本服务只读取风险与渠道领域的生效投影。它不写入规则，也不把内部渠道维度暴露给商户端。</p>
 */
@Service
public class MerchantRiskConfigurationReadinessService {

    private static final String ALL = "ALL";
    private static final String BANK_CARD = "BANK_CARD";
    private static final Set<String> REQUIRED_LIMIT_TYPES = Set.of("SINGLE_MIN", "SINGLE_MAX");

    private final MerchantActivationReadinessMapper readinessMapper;

    /**
     * 创建风险配置就绪检查服务。
     *
     * @param readinessMapper 风险和渠道限额只读投影
     */
    public MerchantRiskConfigurationReadinessService(MerchantActivationReadinessMapper readinessMapper) {
        this.readinessMapper = readinessMapper;
    }

    /**
     * 按同一评估时点检查实际路由能力的风控配置覆盖。
     *
     * @param merchantId 平台商户号
     * @param paymentReadiness 已完成路由展开的支付能力结果
     * @param now 评估时点
     * @return 稳定阻塞编码，空列表表示风险和限额配置完整
     */
    public List<String> evaluate(
            String merchantId,
            MerchantPaymentCapabilityReadinessService.ReadinessResult paymentReadiness,
            LocalDateTime now) {
        if (!StringUtils.hasText(merchantId) || paymentReadiness == null || now == null) {
            throw new IllegalArgumentException("merchant, payment readiness and evaluation time are required");
        }
        List<MerchantPaymentCapabilityReadinessService.RoutedCapability> routes =
                paymentReadiness.routedCapabilities();
        if (routes == null || routes.isEmpty()) {
            return List.of();
        }

        List<MerchantLimitRow> merchantLimits = safe(
                readinessMapper.selectActiveMerchantLimits(merchantId.trim(), now));
        List<Long> channelIds = routes.stream()
                .map(MerchantPaymentCapabilityReadinessService.RoutedCapability::channelId)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        List<ChannelLimitRow> channelLimits = channelIds.isEmpty()
                ? List.of() : safe(readinessMapper.selectActiveChannelLimits(channelIds));
        List<ThreeDsRuleRow> threeDsRules = safe(
                readinessMapper.selectActiveThreeDsRules(merchantId.trim(), now));

        LinkedHashSet<String> issues = new LinkedHashSet<>();
        Set<String> currencies = routes.stream()
                .map(route -> upper(route.capability().currency()))
                .filter(StringUtils::hasText)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        boolean merchantLimitEnabled = readinessMapper.countEnabledRiskFunction(
                merchantId.trim(), "merchantLimit") > 0;
        if (!merchantLimitEnabled && merchantLimits.isEmpty()) {
            issues.add("RISK_MERCHANT_LIMIT_NOT_ENABLED");
        }
        if (currencies.stream().anyMatch(currency -> !limitTypes(merchantLimits, currency)
                .containsAll(REQUIRED_LIMIT_TYPES))) {
            issues.add("RISK_MERCHANT_LIMIT_INCOMPLETE");
        }
        if (routes.stream().anyMatch(route -> !channelLimitTypes(channelLimits, route)
                .containsAll(REQUIRED_LIMIT_TYPES))) {
            issues.add("CHANNEL_LIMIT_INCOMPLETE");
        }
        if (routes.stream().filter(this::bankCard).anyMatch(route ->
                threeDsRules.stream().noneMatch(rule -> covers(rule, route)))) {
            issues.add("THREE_DS_POLICY_NOT_COVERED");
        }
        return List.copyOf(issues);
    }

    /** 返回指定币种当前生效的商户限额类型。 */
    private Set<String> limitTypes(List<MerchantLimitRow> rows, String currency) {
        return rows.stream()
                .filter(row -> upper(currency).equals(upper(row.getCurrency())))
                .map(MerchantLimitRow::getLimitType)
                .map(this::upper)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 返回单个路由能力当前生效且维度匹配的渠道限额类型。 */
    private Set<String> channelLimitTypes(
            List<ChannelLimitRow> rows,
            MerchantPaymentCapabilityReadinessService.RoutedCapability route) {
        var capability = route.capability();
        return rows.stream()
                .filter(row -> java.util.Objects.equals(row.getChannelId(), route.channelId()))
                .filter(row -> dimensionMatches(row.getPaymentMethod(), capability.paymentType()))
                .filter(row -> dimensionMatches(row.getCardBrand(), capability.paymentMethod()))
                .filter(row -> upper(row.getLimitCurrency()).equals(upper(capability.currency())))
                .map(ChannelLimitRow::getLimitType)
                .map(this::upper)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 只有覆盖全部金额和风险等级的 3DS 规则才可作为最终开通兜底策略。
     */
    private boolean covers(
            ThreeDsRuleRow rule,
            MerchantPaymentCapabilityReadinessService.RoutedCapability route) {
        var capability = route.capability();
        return dimensionMatches(rule.getChannelCode(), route.channelCode())
                && dimensionMatches(rule.getPaymentMethod(), capability.paymentType())
                && dimensionMatches(rule.getCardBrand(), capability.paymentMethod())
                && upper(rule.getCurrency()).equals(upper(capability.currency()))
                && ALL.equals(upper(rule.getAmountMatchType()))
                && "ANY".equals(upper(rule.getRiskCondition()));
    }

    private boolean bankCard(MerchantPaymentCapabilityReadinessService.RoutedCapability route) {
        return BANK_CARD.equals(upper(route.capability().paymentType()));
    }

    private boolean dimensionMatches(String configured, String actual) {
        String value = upper(configured);
        return ALL.equals(value) || value.equals(upper(actual));
    }

    private <T> List<T> safe(List<T> rows) {
        return rows == null ? List.of() : rows;
    }

    private String upper(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }
}
