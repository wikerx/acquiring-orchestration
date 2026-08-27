package com.scott.payment.finance.fee.core;

import com.scott.payment.finance.fee.model.FeeCalculationModels.AppliedLimit;
import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.fee.model.FeeCalculationModels.EntryDirection;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeCalculationCommand;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeCalculationResult;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeComponent;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeComponentType;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeEvaluationStatus;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeRuleSnapshot;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeTierSnapshot;
import com.scott.payment.finance.fee.model.FeeCalculationModels.LimitEvaluationStatus;
import com.scott.payment.finance.fee.model.FeeCalculationModels.TierMetric;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeComponentCalculator
 * @date : 2026-08-25 19:20
 * @email : scott_x@163.com
 * @description : 按冻结的商户配置生成费用组件；百分比使用标签币种，固定费和上下限使用 USD，且不读取或推导汇率。
 * @status : create
 */
public class FeeComponentCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    /**
     * 按商户配置计算单个费用规则的组件和清分阶段求值状态。
     *
     * @param command 标签金额、规则、阶梯累计和舍入模式
     * @return 可直接持久化的标签币种百分比组件和 USD 固定费用事实
     * @throws IllegalArgumentException 配置不完整、币种精度冲突或暂不支持的阶梯规则
     */
    public FeeCalculationResult calculate(FeeCalculationCommand command) {
        FeeRuleSnapshot rule = command.rule();
        PricingResolution pricing = resolvePricing(command);
        Money fixed = rounded(pricing.finalTerms().fixedFeeUsd(), command.roundingMode());
        Money minimum = rounded(pricing.finalTerms().minimumFeeUsd(), command.roundingMode());
        Money maximum = rounded(pricing.finalTerms().maximumFeeUsd(), command.roundingMode());
        validateSameCurrencyLimits(minimum, maximum);

        List<FeeComponent> components = new ArrayList<>();
        pricing.percentageSlices().forEach(slice -> {
            Money percentage = percentage(slice.basis(), slice.percentageRate(), command.roundingMode());
            if (percentage.amount().signum() > 0) {
                components.add(new FeeComponent(FeeComponentType.PERCENTAGE, EntryDirection.DEBIT,
                        percentage, slice.basis(), slice.percentageRate(), rule.ruleId(), slice.tierId()));
            }
        });
        if (fixed != null && fixed.amount().signum() > 0) {
            components.add(new FeeComponent(FeeComponentType.FIXED, EntryDirection.DEBIT,
                    fixed, null, null, rule.ruleId(), pricing.matchedTierId()));
        }

        if (!canEvaluateAtClearing(command.labelAmount(), components, minimum, maximum)) {
            return new FeeCalculationResult(pricing.matchedTierId(), pricing.matchedTierIds(), components,
                    FeeEvaluationStatus.PENDING_SETTLEMENT_RATE,
                    minimum == null && maximum == null
                            ? LimitEvaluationStatus.NOT_REQUIRED
                            : LimitEvaluationStatus.PENDING_SETTLEMENT_RATE,
                    AppliedLimit.NONE, null, minimum, maximum);
        }

        Money calculationCurrency = resolveCalculationCurrency(command.labelAmount(), components, minimum, maximum);
        BigDecimal rawAmount = components.stream()
                .map(component -> component.amount().amount())
                .reduce(BigDecimal.ZERO, (left, right) -> left.add(right, CALCULATION_CONTEXT));
        BigDecimal finalAmount = rawAmount;
        AppliedLimit appliedLimit = AppliedLimit.NONE;
        if (minimum != null && finalAmount.compareTo(minimum.amount()) < 0) {
            BigDecimal adjustment = minimum.amount().subtract(finalAmount, CALCULATION_CONTEXT);
            components.add(limitAdjustment(adjustment, EntryDirection.DEBIT, calculationCurrency,
                    rule.ruleId(), pricing.matchedTierId()));
            finalAmount = minimum.amount();
            appliedLimit = AppliedLimit.MINIMUM;
        }
        if (maximum != null && finalAmount.compareTo(maximum.amount()) > 0) {
            BigDecimal adjustment = finalAmount.subtract(maximum.amount(), CALCULATION_CONTEXT);
            components.add(limitAdjustment(adjustment, EntryDirection.CREDIT, calculationCurrency,
                    rule.ruleId(), pricing.matchedTierId()));
            finalAmount = maximum.amount();
            appliedLimit = AppliedLimit.MAXIMUM;
        }

        Money finalFee = new Money(finalAmount, calculationCurrency.currency(),
                calculationCurrency.exponent()).rounded(command.roundingMode());
        return new FeeCalculationResult(pricing.matchedTierId(), pricing.matchedTierIds(), components,
                FeeEvaluationStatus.FINAL_AT_CLEARING,
                minimum == null && maximum == null
                        ? LimitEvaluationStatus.NOT_REQUIRED
                        : LimitEvaluationStatus.FINAL_AT_CLEARING,
                appliedLimit, finalFee, minimum, maximum);
    }

    /** 将标准规则或现有整笔适用阶梯规则解析为本次计价条款。 */
    private PricingResolution resolvePricing(FeeCalculationCommand command) {
        FeeRuleSnapshot rule = command.rule();
        if (rule.feeMode() == FeeMode.STANDARD) {
            RuleTerms terms = new RuleTerms(rule.percentageRate(), rule.fixedFeeUsd(),
                    rule.minimumFeeUsd(), rule.maximumFeeUsd());
            return new PricingResolution(
                    List.of(new PercentageSlice(command.labelAmount(), terms.percentageRate(), null)),
                    terms, null, List.of());
        }
        if (rule.tierMetric() == null) {
            throw new IllegalArgumentException("tier metric is required");
        }

        BigDecimal reachedValue = reachedValue(command);
        FeeTierSnapshot tier = sortedTiers(command).stream()
                .filter(candidate -> reachedValue.compareTo(candidate.lowerBound()) >= 0)
                .filter(candidate -> candidate.upperBound() == null
                        || reachedValue.compareTo(candidate.upperBound()) < 0)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("tier is not configured for reached value"));
        RuleTerms terms = terms(tier);
        return new PricingResolution(
                List.of(new PercentageSlice(command.labelAmount(), terms.percentageRate(), tier.tierId())),
                terms, tier.tierId(), List.of(tier.tierId()));
    }

    private BigDecimal reachedValue(FeeCalculationCommand command) {
        if (command.rule().tierMetric() == TierMetric.COUNT) {
            return BigDecimal.valueOf(command.tierContext().countBefore()).add(BigDecimal.ONE);
        }
        return command.tierContext().amountUsdBefore()
                .add(command.tierContext().currentAmountUsd(), CALCULATION_CONTEXT);
    }

    private List<FeeTierSnapshot> sortedTiers(FeeCalculationCommand command) {
        if (command.tiers().isEmpty()) {
            throw new IllegalArgumentException("tier configuration is required");
        }
        command.tiers().forEach(this::validateTierBounds);
        List<FeeTierSnapshot> sortedTiers = command.tiers().stream()
                .sorted(Comparator.comparing(FeeTierSnapshot::lowerBound))
                .toList();
        validateCountTierBounds(sortedTiers, command.rule().tierMetric());
        validateTierContinuity(sortedTiers);
        return sortedTiers;
    }

    private void validateCountTierBounds(List<FeeTierSnapshot> tiers, TierMetric tierMetric) {
        if (tierMetric != TierMetric.COUNT) {
            return;
        }
        boolean fractionalBoundary = tiers.stream()
                .anyMatch(tier -> hasFraction(tier.lowerBound())
                        || tier.upperBound() != null && hasFraction(tier.upperBound()));
        if (fractionalBoundary) {
            throw new IllegalArgumentException("count tier boundaries must be integers");
        }
    }

    private boolean hasFraction(BigDecimal value) {
        return value.stripTrailingZeros().scale() > 0;
    }

    private void validateTierContinuity(List<FeeTierSnapshot> tiers) {
        if (tiers.get(0).lowerBound().compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalArgumentException("tier intervals must start at zero");
        }
        if (tiers.get(tiers.size() - 1).upperBound() != null) {
            throw new IllegalArgumentException("last tier upper bound must be empty");
        }
        for (int index = 0; index < tiers.size() - 1; index++) {
            FeeTierSnapshot current = tiers.get(index);
            FeeTierSnapshot next = tiers.get(index + 1);
            if (current.upperBound() == null
                    || current.upperBound().compareTo(next.lowerBound()) != 0) {
                throw new IllegalArgumentException("tier intervals must be continuous and non-overlapping");
            }
        }
    }

    private RuleTerms terms(FeeTierSnapshot tier) {
        BigDecimal percentageRate = tier.percentageRate() == null ? BigDecimal.ZERO : tier.percentageRate();
        if (percentageRate.signum() < 0) {
            throw new IllegalArgumentException("tier percentage rate must not be negative");
        }
        return new RuleTerms(percentageRate, tier.fixedFeeUsd(), tier.minimumFeeUsd(), tier.maximumFeeUsd());
    }

    private void validateTierBounds(FeeTierSnapshot tier) {
        if (tier.tierId() == null || tier.lowerBound() == null || tier.lowerBound().signum() < 0) {
            throw new IllegalArgumentException("tier id and non-negative lower bound are required");
        }
        if (tier.upperBound() != null && tier.upperBound().compareTo(tier.lowerBound()) <= 0) {
            throw new IllegalArgumentException("tier upper bound must exceed lower bound");
        }
    }

    private Money percentage(Money basis,
                                      BigDecimal percentageRate,
                                      java.math.RoundingMode roundingMode) {
        BigDecimal amount = basis.amount()
                .multiply(percentageRate, CALCULATION_CONTEXT)
                .divide(ONE_HUNDRED, CALCULATION_CONTEXT);
        return new Money(amount, basis.currency(), basis.exponent()).rounded(roundingMode);
    }

    private Money rounded(Money amount, java.math.RoundingMode roundingMode) {
        return amount == null ? null : amount.rounded(roundingMode);
    }

    private void validateSameCurrencyLimits(Money minimum, Money maximum) {
        if (minimum != null && maximum != null && minimum.sameCurrency(maximum)
                && minimum.amount().compareTo(maximum.amount()) > 0) {
            throw new IllegalArgumentException("minimum fee must not exceed maximum fee");
        }
    }

    private boolean canEvaluateAtClearing(Money labelAmount,
                                          List<FeeComponent> components,
                                          Money minimum,
                                          Money maximum) {
        Set<String> currencies = new LinkedHashSet<>();
        Set<Integer> exponents = new LinkedHashSet<>();
        if (components.isEmpty() && minimum == null && maximum == null) {
            addCurrency(currencies, exponents, labelAmount);
        }
        components.forEach(component -> addCurrency(currencies, exponents, component.amount()));
        addCurrency(currencies, exponents, minimum);
        addCurrency(currencies, exponents, maximum);
        return currencies.size() <= 1 && exponents.size() <= 1;
    }

    private Money resolveCalculationCurrency(Money labelAmount,
                                                      List<FeeComponent> components,
                                                      Money minimum,
                                                      Money maximum) {
        if (!components.isEmpty()) {
            return components.get(0).amount();
        }
        if (minimum != null) {
            return minimum;
        }
        if (maximum != null) {
            return maximum;
        }
        return new Money(BigDecimal.ZERO, labelAmount.currency(), labelAmount.exponent());
    }

    private FeeComponent limitAdjustment(BigDecimal amount,
                                         EntryDirection direction,
                                         Money currency,
                                         Long ruleId,
                                         Long tierId) {
        return new FeeComponent(FeeComponentType.LIMIT_ADJUSTMENT, direction,
                new Money(amount, currency.currency(), currency.exponent()),
                null, null, ruleId, tierId);
    }

    private void addCurrency(Set<String> currencies, Set<Integer> exponents, Money amount) {
        if (amount != null) {
            currencies.add(amount.currency());
            exponents.add(amount.exponent());
        }
    }

    private record RuleTerms(BigDecimal percentageRate,
                             Money fixedFeeUsd,
                             Money minimumFeeUsd,
                             Money maximumFeeUsd) {
    }

    private record PercentageSlice(Money basis, BigDecimal percentageRate, Long tierId) {
    }

    private record PricingResolution(List<PercentageSlice> percentageSlices,
                                     RuleTerms finalTerms,
                                     Long matchedTierId,
                                     List<Long> matchedTierIds) {
    }
}
