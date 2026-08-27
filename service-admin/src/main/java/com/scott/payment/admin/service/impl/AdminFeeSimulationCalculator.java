package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationResponse;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeRuleDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeRuleTierDO;
import com.scott.payment.component.core.iso.IsoCurrencyResolver;
import com.scott.payment.finance.fee.core.FeeConversionPreviewCalculator;
import com.scott.payment.finance.fee.core.FeeComponentCalculator;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeCalculationCommand;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeComponentType;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeRuleSnapshot;
import com.scott.payment.finance.fee.model.FeeCalculationModels.TierContext;
import com.scott.payment.finance.fee.model.FeeConversionPreviewModels.FeeConversionPreviewCommand;
import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.reserve.core.ReserveCalculator;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveHoldCommand;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

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

    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;
    private static final int DISPLAY_SCALE = 2;
    private static final int USD_EXPONENT = 2;
    private static final RoundingMode FEE_ROUNDING_MODE = RoundingMode.HALF_UP;

    private final FeeComponentCalculator feeComponentCalculator = new FeeComponentCalculator();
    private final ReserveCalculator reserveCalculator = new ReserveCalculator();
    private final FeeConversionPreviewCalculator feeConversionPreviewCalculator =
            new FeeConversionPreviewCalculator();

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

        int labelExponent = resolveCurrencyExponent(request.getLabelCurrency());
        Money labelAmount = new Money(request.getLabelAmount(), request.getLabelCurrency(),
                labelExponent);
        FeeRuleSnapshot configuredRule = new FeeRuleSnapshot(rule.getId(), FeeMode.STANDARD, percentageRate,
                usdAmount(fixedUsd), optionalUsdAmount(minimumUsd), optionalUsdAmount(maximumUsd), null);
        var componentResult = feeComponentCalculator.calculate(new FeeCalculationCommand(labelAmount, configuredRule,
                List.of(), TierContext.empty(), FEE_ROUNDING_MODE));
        Map<String, BigDecimal> directRates = labelAmount.currency().equals("USD")
                ? Map.of()
                : Map.of(labelAmount.currency(), labelToUsdRate);
        var preview = feeConversionPreviewCalculator.calculate(new FeeConversionPreviewCommand(
                componentResult, "USD", USD_EXPONENT, directRates));
        BigDecimal percentageFeeLabel = componentResult.components().stream()
                .filter(component -> component.componentType() == FeeComponentType.PERCENTAGE)
                .map(component -> component.amount().amount())
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right, CALCULATION_CONTEXT));
        BigDecimal labelAmountUsd = request.getLabelAmount().multiply(labelToUsdRate, CALCULATION_CONTEXT);
        BigDecimal effectiveReserveRate = zero(reserveRate);
        boolean appliesReserve = "TRANSACTION_FEE".equalsIgnoreCase(request.getFeeCategory());
        BigDecimal reserveAmountLabel = appliesReserve
                ? reserveCalculator.hold(new ReserveHoldCommand(labelAmount, effectiveReserveRate,
                        FEE_ROUNDING_MODE)).amount().amount()
                : BigDecimal.ZERO;
        BigDecimal reserveAmountUsd = appliesReserve
                ? reserveAmountLabel.multiply(labelToUsdRate, CALCULATION_CONTEXT)
                : BigDecimal.ZERO;

        FeeSimulationResponse response = new FeeSimulationResponse();
        response.setMatchedRuleId(rule.getId());
        response.setMatchedTierId(tier == null ? null : tier.getId());
        response.setPercentageFeeLabel(percentageFeeLabel);
        response.setPercentageFeeCurrency(request.getLabelCurrency());
        response.setRawFeeUsd(preview.rawFee().amount());
        response.setFinalFeeUsd(preview.finalFee().amount());
        response.setLabelAmountUsd(labelAmountUsd);
        response.setReserveRate(effectiveReserveRate);
        response.setReserveAmountLabel(reserveAmountLabel);
        response.setReserveAmountCurrency(request.getLabelCurrency());
        response.setReserveAmountUsd(reserveAmountUsd);
        response.setEstimatedNetSettlementUsd(labelAmountUsd
                .subtract(preview.finalFee().amount(), CALCULATION_CONTEXT)
                .subtract(reserveAmountUsd, CALCULATION_CONTEXT));
        response.setAppliedLimit(preview.appliedLimit().name());
        response.setLabelToUsdRate(labelToUsdRate);
        response.setFormulaSnapshot(buildFormula(request, labelToUsdRate,
                percentageRate, fixedUsd, minimumUsd, maximumUsd));
        return response;
    }

    private int resolveCurrencyExponent(String currency) {
        int exponent = IsoCurrencyResolver.resolve(currency)
                .orElseThrow(() -> new IllegalArgumentException("unsupported ISO 4217 label currency"))
                .defaultFractionDigits();
        if (exponent < 0 || exponent > 8) {
            throw new IllegalArgumentException("label currency exponent must be between 0 and 8");
        }
        return exponent;
    }

    private Money usdAmount(BigDecimal amount) {
        return new Money(zero(amount), "USD", USD_EXPONENT);
    }

    private Money optionalUsdAmount(BigDecimal amount) {
        return amount == null ? null : usdAmount(amount);
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
        StringBuilder formula;
        if (percentageRate.signum() == 0) {
            formula = new StringBuilder("USD ").append(displayDecimal(fixedUsd));
        } else {
            formula = new StringBuilder(request.getLabelCurrency())
                    .append(" ").append(displayDecimal(request.getLabelAmount()))
                    .append(" * ").append(displayDecimal(percentageRate)).append("% * ")
                    .append(displayRate(labelToUsdRate));
            if (fixedUsd.signum() != 0) {
                formula.append(" + USD ").append(displayDecimal(fixedUsd));
            }
        }
        if (minimumUsd != null) {
            formula.append("; min=USD ").append(displayDecimal(minimumUsd));
        }
        if (maximumUsd != null) {
            formula.append("; max=USD ").append(displayDecimal(maximumUsd));
        }
        return formula.toString();
    }

    private String displayDecimal(BigDecimal value) {
        return zero(value).setScale(DISPLAY_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    private String displayRate(BigDecimal value) {
        return zero(value).stripTrailingZeros().toPlainString();
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
