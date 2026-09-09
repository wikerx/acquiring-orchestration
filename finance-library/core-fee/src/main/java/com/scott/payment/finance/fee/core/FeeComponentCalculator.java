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

    /**
     * 百分比换算基数 100，用于把百分数转换为比例值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    /**
     * 财务计算统一 MathContext，约束中间计算精度并避免过早舍入。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
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

    /**
     * 计算本笔交易完成后的阶梯命中值；笔数口径加一，金额口径沿用调用方提供的 USD 累计事实。
     *
     * @param command 包含阶梯指标和本笔前累计事实的计算命令
     * @return 用于匹配左闭右开阶梯区间的累计值
     */
    private BigDecimal reachedValue(FeeCalculationCommand command) {
        if (command.rule().tierMetric() == TierMetric.COUNT) {
            return BigDecimal.valueOf(command.tierContext().countBefore()).add(BigDecimal.ONE);
        }
        return command.tierContext().amountUsdBefore()
                .add(command.tierContext().currentAmountUsd(), CALCULATION_CONTEXT);
    }

    /**
     * 校验并按下界排序冻结的阶梯，确保区间从零开始、连续且最后一档无上界。
     *
     * @param command 含冻结阶梯列表的计算命令
     * @return 按下界升序排列的不可变阶梯列表
     */
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

    /**
     * 按标签金额和标签币种计算百分比组件，仅在组件最终落地处按该币种 exponent 舍入。
     *
     * @param basis 标签币种计算基数
     * @param percentageRate 百分比数值，2.3 表示 2.3%
     * @param roundingMode 费用版本冻结的舍入规则
     * @return 与标签金额同币种、同 exponent 的非负费用组件
     */
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

    /**
     * 仅在最低费与最高费币种及 exponent 一致时校验上下限顺序；跨币种限额必须留到结算阶段按统一汇率求值。
     *
     * @param minimum USD 最低费用，可为空
     * @param maximum USD 最高费用，可为空
     * @throws IllegalArgumentException 同币种最低费用大于最高费用时抛出
     */
    private void validateSameCurrencyLimits(Money minimum, Money maximum) {
        if (minimum != null && maximum != null && minimum.sameCurrency(maximum)
                && minimum.amount().compareTo(maximum.amount()) > 0) {
            throw new IllegalArgumentException("minimum fee must not exceed maximum fee");
        }
    }

    /**
     * 判断费用组件与 USD 限额是否已经处于同一币种和 exponent；跨币种时必须留待结算统一汇率求值。
     *
     * @param labelAmount 标签金额，用于无费用组件时确定零值币种
     * @param components 已生成的标签币种百分比和 USD 固定费组件
     * @param minimum USD 最低费用
     * @param maximum USD 最高费用
     * @return 清分阶段无需汇率即可形成最终费用时返回 true
     */
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

    /**
     * 在已确认单币种后解析最终费用的币种和 exponent，不进行任何汇率转换。
     *
     * @param labelAmount 标签金额
     * @param components 已生成费用组件
     * @param minimum 同币种最低费用
     * @param maximum 同币种最高费用
     * @return 用于构造最终费用和限额调整的币种载体
     */
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

    /**
     * 将最低或最高费用命中差额保存为独立组件，保留原始费用组件以便后续审计和冲正。
     *
     * @param amount 非负调整金额
     * @param direction 最低费补扣为 DEBIT，最高费回冲为 CREDIT
     * @param currency 已确认的单币种载体
     * @param ruleId 来源费用规则主键
     * @param tierId 来源阶梯主键，标准费率为空
     * @return 可持久化的限额调整组件
     */
    private FeeComponent limitAdjustment(BigDecimal amount,
                                         EntryDirection direction,
                                         Money currency,
                                         Long ruleId,
                                         Long tierId) {
        return new FeeComponent(FeeComponentType.LIMIT_ADJUSTMENT, direction,
                new Money(amount, currency.currency(), currency.exponent()),
                null, null, ruleId, tierId);
    }

    /**
     * 分别收集币种与 exponent，用于判定清分阶段能否在不引入汇率的前提下完成费用限额计算。
     *
     * @param currencies 已发现的币种集合
     * @param exponents 已发现的币种精度集合
     * @param amount 待检查金额，可为空
     */
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
