package com.scott.payment.clearing.domain.service;

import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 清分费用规则适用性判断。
 *
 * <p>该类只判断动作、支付维度和收费触发条件，不计算金额、不读取累计事实，也不访问外部资源。
 * 费用计算和阶梯累计锁定必须共同使用本判断，避免未命中规则产生无效数据库锁或累计。</p>
 */
public final class ClearingFeeRuleMatcher {

    private static final Set<String> SETTLEMENT_BEARING_TRANSACTION_TYPES = Set.of(
            "PAYMENT", "CAPTURE", "PRE_AUTH_COMPLETION", "REFUND", "CHARGEBACK", "REPRESENTMENT");

    private ClearingFeeRuleMatcher() {
    }

    /**
     * 判断冻结费用规则是否适用于当前终态动作。
     *
     * @param operation 当前数据库权威动作
     * @param paymentType 支付类型
     * @param paymentMethod 支付方式或品牌
     * @param occurredRiskServices 当前动作实际调用的风险服务
     * @param settlementCurrency 冻结费用版本的商户结算币种
     * @param rule 冻结费用规则
     * @return 维度和收费触发条件均命中时返回 true
     */
    public static boolean matches(ClearingOperationFacts operation,
                                  String paymentType,
                                  String paymentMethod,
                                  Set<String> occurredRiskServices,
                                  String settlementCurrency,
                                  FeeRuleConfigurationSnapshot rule) {
        Objects.requireNonNull(operation, "clearing operation is required");
        Objects.requireNonNull(rule, "fee rule is required");
        Set<String> riskServices = occurredRiskServices == null ? Set.of() : occurredRiskServices;
        if ("SETTLEMENT_FX_FEE".equalsIgnoreCase(rule.feeCategory())) {
            if (!SETTLEMENT_BEARING_TRANSACTION_TYPES.contains(operation.transactionType())) {
                return false;
            }
            if (operation.labelCurrency() != null
                    && operation.labelCurrency().equalsIgnoreCase(settlementCurrency)) {
                return false;
            }
        }
        return matchesDimension(rule.transactionType(), operation.transactionType())
                && matchesDimension(rule.paymentType(), paymentType)
                && matchesDimension(rule.paymentMethod(), paymentMethod)
                && chargeTriggered(operation.transactionStatus(), riskServices, rule);
    }

    private static boolean matchesDimension(String configured, String actual) {
        if (configured == null || configured.isBlank()) {
            throw new IllegalArgumentException("fee rule dimension is required");
        }
        return "ALL".equalsIgnoreCase(configured)
                || actual != null && !actual.isBlank() && configured.equalsIgnoreCase(actual);
    }

    private static boolean chargeTriggered(String transactionStatus,
                                           Set<String> occurredRiskServices,
                                           FeeRuleConfigurationSnapshot rule) {
        if (rule.chargeTrigger() == null || rule.chargeTrigger().isBlank()) {
            throw new IllegalArgumentException("fee charge trigger is required");
        }
        String trigger = rule.chargeTrigger().trim().toUpperCase(Locale.ROOT);
        return switch (trigger) {
            case "SUCCESS", "NOT_APPLICABLE" -> "SUCCESS".equals(transactionStatus);
            case "SUCCESS_OR_FAILURE" -> Set.of("SUCCESS", "FAILED").contains(transactionStatus);
            case "ON_CALL" -> occurredRiskServices.stream()
                    .anyMatch(value -> value != null && value.equalsIgnoreCase(rule.riskServiceType()));
            case "NO_CHARGE" -> false;
            default -> throw new IllegalArgumentException("unsupported fee charge trigger");
        };
    }
}
