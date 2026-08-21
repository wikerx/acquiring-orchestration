package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationResponse;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeRuleDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeRuleTierDO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.Comparator;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFeeSimulationCalculator
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 无副作用费用试算器；使用调用方已从系统解析的标签币种到 USD 正向结算汇率完成高精度计算。
 * @status : create
 */
@Component
public class AdminFeeSimulationCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    /**
     * 兼容不涉及保证金的独立费用计算调用。
     *
     * @param request 试算输入
     * @param rule 匹配后的费用规则
     * @param tiers 规则阶梯
     * @param labelToUsdRate 标签币种到 USD 的直接汇率
     * @return 不计滚动保证金的试算结果
     */
    public FeeSimulationResponse calculate(FeeSimulationRequest request,
                                            FeeRuleDO rule,
                                            List<FeeRuleTierDO> tiers,
                                            BigDecimal labelToUsdRate) {
        return calculate(request, rule, tiers, labelToUsdRate, BigDecimal.ZERO);
    }

    /**
     * 按指定规则试算单笔费用，阶梯阈值包含当前交易。
     *
     * @param request 试算输入
     * @param rule 匹配后的费用规则
     * @param tiers 规则阶梯
     * @param labelToUsdRate 系统解析的标签币种到 USD 正向结算汇率
     * @param reserveRate 当前生效版本的滚动保证金比例
     * @return USD 最终费用和公式快照
     */
    public FeeSimulationResponse calculate(FeeSimulationRequest request,
                                            FeeRuleDO rule,
                                            List<FeeRuleTierDO> tiers,
                                            BigDecimal labelToUsdRate,
                                            BigDecimal reserveRate) {
        validate(request, rule, labelToUsdRate);
        FeeRuleTierDO tier = selectTier(request, rule, tiers, labelToUsdRate);
        BigDecimal percentageRate = tier == null ? zero(rule.getPercentageRate()) : zero(tier.getPercentageRate());
        BigDecimal fixedUsd = tier == null ? zero(rule.getFixedAmountUsd()) : zero(tier.getFixedAmountUsd());
        BigDecimal minimumUsd = tier == null ? rule.getMinimumAmountUsd() : tier.getMinimumAmountUsd();
        BigDecimal maximumUsd = tier == null ? rule.getMaximumAmountUsd() : tier.getMaximumAmountUsd();

        BigDecimal percentageFeeLabel = request.getLabelAmount()
                .multiply(percentageRate, CALCULATION_CONTEXT)
                .divide(ONE_HUNDRED, CALCULATION_CONTEXT);
        BigDecimal percentageFeeUsd = percentageFeeLabel
                .multiply(labelToUsdRate, CALCULATION_CONTEXT);
        BigDecimal rawFeeUsd = percentageFeeUsd.add(fixedUsd, CALCULATION_CONTEXT);
        BigDecimal finalFeeUsd = rawFeeUsd;
        String appliedLimit = "NONE";
        if (minimumUsd != null && finalFeeUsd.compareTo(minimumUsd) < 0) {
            finalFeeUsd = minimumUsd;
            appliedLimit = "MINIMUM";
        }
        if (maximumUsd != null && finalFeeUsd.compareTo(maximumUsd) > 0) {
            finalFeeUsd = maximumUsd;
            appliedLimit = "MAXIMUM";
        }
        BigDecimal labelAmountUsd = request.getLabelAmount().multiply(labelToUsdRate, CALCULATION_CONTEXT);
        BigDecimal effectiveReserveRate = zero(reserveRate);
        BigDecimal reserveAmountUsd = "TRANSACTION_FEE".equalsIgnoreCase(request.getFeeCategory())
                ? labelAmountUsd.multiply(effectiveReserveRate, CALCULATION_CONTEXT)
                        .divide(ONE_HUNDRED, CALCULATION_CONTEXT)
                : BigDecimal.ZERO;

        FeeSimulationResponse response = new FeeSimulationResponse();
        response.setMatchedRuleId(rule.getId());
        response.setMatchedTierId(tier == null ? null : tier.getId());
        response.setPercentageFeeLabel(percentageFeeLabel);
        response.setPercentageFeeCurrency(request.getLabelCurrency());
        response.setRawFeeUsd(rawFeeUsd);
        response.setFinalFeeUsd(finalFeeUsd);
        response.setLabelAmountUsd(labelAmountUsd);
        response.setReserveRate(effectiveReserveRate);
        response.setReserveAmountUsd(reserveAmountUsd);
        response.setEstimatedNetSettlementUsd(labelAmountUsd
                .subtract(finalFeeUsd, CALCULATION_CONTEXT)
                .subtract(reserveAmountUsd, CALCULATION_CONTEXT));
        response.setAppliedLimit(appliedLimit);
        response.setLabelToUsdRate(labelToUsdRate);
        response.setFormulaSnapshot(buildFormula(request, labelToUsdRate,
                percentageRate, fixedUsd, minimumUsd, maximumUsd));
        return response;
    }

    private FeeRuleTierDO selectTier(FeeSimulationRequest request,
                                     FeeRuleDO rule,
                                     List<FeeRuleTierDO> tiers,
                                     BigDecimal labelToUsdRate) {
        if (!"TIER".equals(rule.getFeeMode())) {
            return null;
        }
        BigDecimal reachedValue;
        if ("COUNT".equals(rule.getTierMetric())) {
            reachedValue = BigDecimal.valueOf(request.getMonthlyCountBefore()).add(BigDecimal.ONE);
        } else if ("AMOUNT".equals(rule.getTierMetric())) {
            BigDecimal currentAmountUsd = request.getLabelAmount()
                    .multiply(labelToUsdRate, CALCULATION_CONTEXT);
            reachedValue = request.getMonthlyAmountUsdBefore().add(currentAmountUsd, CALCULATION_CONTEXT);
        } else {
            throw new IllegalArgumentException("unsupported tier metric");
        }
        return (tiers == null ? List.<FeeRuleTierDO>of() : tiers).stream()
                .sorted(Comparator.comparing(FeeRuleTierDO::getLowerBound))
                .filter(item -> reachedValue.compareTo(item.getLowerBound()) >= 0)
                .filter(item -> item.getUpperBound() == null || reachedValue.compareTo(item.getUpperBound()) < 0)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("tier is not configured for reached value"));
    }

    private void validate(FeeSimulationRequest request, FeeRuleDO rule, BigDecimal labelToUsdRate) {
        if (request == null || rule == null) {
            throw new IllegalArgumentException("simulation request and rule are required");
        }
        if (request.getLabelAmount() == null || request.getLabelAmount().signum() < 0) {
            throw new IllegalArgumentException("label amount must not be negative");
        }
        if (labelToUsdRate == null || labelToUsdRate.signum() <= 0) {
            throw new IllegalArgumentException("label currency to USD direct rate must be positive");
        }
        if ("USD".equalsIgnoreCase(request.getLabelCurrency())
                && labelToUsdRate.compareTo(BigDecimal.ONE) != 0) {
            throw new IllegalArgumentException("USD direct rate must be 1");
        }
    }

    private String buildFormula(FeeSimulationRequest request,
                                BigDecimal labelToUsdRate,
                                BigDecimal percentageRate,
                                BigDecimal fixedUsd,
                                BigDecimal minimumUsd,
                                BigDecimal maximumUsd) {
        return request.getLabelCurrency() + " " + request.getLabelAmount().toPlainString()
                + " * " + percentageRate.toPlainString() + "% * "
                + labelToUsdRate.toPlainString() + " + USD " + fixedUsd.toPlainString()
                + "; min=" + valueOrDash(minimumUsd) + "; max=" + valueOrDash(maximumUsd);
    }

    private String valueOrDash(BigDecimal value) {
        return value == null ? "-" : "USD " + value.toPlainString();
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
