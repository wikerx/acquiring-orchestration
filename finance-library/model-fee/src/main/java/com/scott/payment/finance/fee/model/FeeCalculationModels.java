package com.scott.payment.finance.fee.model;

import com.scott.payment.finance.money.model.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeCalculationModels
 * @date : 2026-08-25 19:20
 * @email : scott_x@163.com
 * @description : 定义清分和后台试算共用的不可变费用计算契约；百分比按标签币种，固定费和上下限按 USD，不包含汇率或数据库对象。
 * @status : create
 */
public final class FeeCalculationModels {

    private FeeCalculationModels() {
    }

    /** 费用规则计价模式。 */
    public enum FeeMode {
        /**
         * STANDARD 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        STANDARD,
        TIER
    }

    /** 阶梯累计指标。 */
    public enum TierMetric {
        /**
         * COUNT 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        COUNT,
        AMOUNT
    }

    /** 清分费用原子组件类型。 */
    public enum FeeComponentType {
        /**
         * PERCENTAGE 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        PERCENTAGE,
        /**
         * FIXED 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        FIXED,
        LIMIT_ADJUSTMENT
    }

    /** 费用组件对商户应结金额的方向。 */
    public enum EntryDirection {
        /**
         * DEBIT 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        DEBIT,
        CREDIT
    }

    /** 当前费用组是否已经得到单币种最终金额。 */
    public enum FeeEvaluationStatus {
        /**
         * FINAL AT CLEARING 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        FINAL_AT_CLEARING,
        PENDING_SETTLEMENT_RATE
    }

    /** 最低和最高费用的求值状态。 */
    public enum LimitEvaluationStatus {
        /**
         * NOT REQUIRED 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        NOT_REQUIRED,
        /**
         * FINAL AT CLEARING 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        FINAL_AT_CLEARING,
        PENDING_SETTLEMENT_RATE
    }

    /** 同币种限额求值后实际采用的边界。 */
    public enum AppliedLimit {
        /**
         * NONE 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        NONE,
        /**
         * MINIMUM 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        MINIMUM,
        MAXIMUM
    }

    /**
     * 不可变费用规则快照。
     *
     * @param ruleId 费用规则主键
     * @param feeMode 标准或阶梯计价模式
     * @param percentageRate 百分比数值，2.3 表示 2.3%
     * @param fixedFeeUsd 固定费用 USD 金额，未配置时允许为空
     * @param minimumFeeUsd 最低费用 USD 金额，未配置时允许为空
     * @param maximumFeeUsd 最高费用 USD 金额，未配置时允许为空
     * @param tierMetric 阶梯指标，标准费率时为空
     */
    public record FeeRuleSnapshot(Long ruleId,
                                  FeeMode feeMode,
                                  BigDecimal percentageRate,
                                  Money fixedFeeUsd,
                                  Money minimumFeeUsd,
                                  Money maximumFeeUsd,
                                  TierMetric tierMetric) {

        public FeeRuleSnapshot {
            Objects.requireNonNull(ruleId, "rule id is required");
            Objects.requireNonNull(feeMode, "fee mode is required");
            percentageRate = percentageRate == null ? BigDecimal.ZERO : percentageRate;
            if (percentageRate.signum() < 0) {
                throw new IllegalArgumentException("percentage rate must not be negative");
            }
            requireNonNegative(fixedFeeUsd, "fixed fee");
            requireNonNegative(minimumFeeUsd, "minimum fee");
            requireNonNegative(maximumFeeUsd, "maximum fee");
            requireUsd(fixedFeeUsd, "fixed fee");
            requireUsd(minimumFeeUsd, "minimum fee");
            requireUsd(maximumFeeUsd, "maximum fee");
            if (feeMode == FeeMode.STANDARD && tierMetric != null) {
                throw new IllegalArgumentException("standard fee rule must not declare tier metric");
            }
            if (feeMode == FeeMode.TIER && tierMetric == null) {
                throw new IllegalArgumentException("tier fee rule requires tier metric");
            }
        }
    }

    /**
     * 阶梯费率快照。
     *
     * @param tierId 阶梯主键
     * @param lowerBound 累计下界，包含
     * @param upperBound 累计上界，不包含；末档为空
     * @param percentageRate 当前档百分比数值
     * @param fixedFeeUsd 当前档固定费用，币种固定为 USD
     * @param minimumFeeUsd 当前档最低费用，币种固定为 USD
     * @param maximumFeeUsd 当前档最高费用，币种固定为 USD
     */
    public record FeeTierSnapshot(Long tierId,
                                  BigDecimal lowerBound,
                                  BigDecimal upperBound,
                                  BigDecimal percentageRate,
                                  Money fixedFeeUsd,
                                  Money minimumFeeUsd,
                                  Money maximumFeeUsd) {

        public FeeTierSnapshot {
            requireNonNegative(fixedFeeUsd, "tier fixed fee");
            requireNonNegative(minimumFeeUsd, "tier minimum fee");
            requireNonNegative(maximumFeeUsd, "tier maximum fee");
            requireUsd(fixedFeeUsd, "tier fixed fee");
            requireUsd(minimumFeeUsd, "tier minimum fee");
            requireUsd(maximumFeeUsd, "tier maximum fee");
        }
    }

    /**
     * 当前交易的阶梯累计事实。
     *
     * @param countBefore 当前交易前累计笔数
     * @param amountUsdBefore 当前交易前按既有业务口径归一的 USD 累计金额
     * @param currentAmountUsd 当前交易按相同口径归一的 USD 金额
     */
    public record TierContext(long countBefore,
                              BigDecimal amountUsdBefore,
                              BigDecimal currentAmountUsd) {

        public TierContext {
            if (countBefore < 0) {
                throw new IllegalArgumentException("tier count before must not be negative");
            }
            amountUsdBefore = requireNonNegative(amountUsdBefore, "tier USD amount before");
            currentAmountUsd = requireNonNegative(currentAmountUsd, "current tier USD amount");
        }

        /**
         * 创建不使用阶梯累计的零值上下文。
         *
         * @return 零笔数、零历史 USD 金额和零当前 USD 金额
         */
        public static TierContext empty() {
            return new TierContext(0L, BigDecimal.ZERO, BigDecimal.ZERO);
        }
    }

    /**
     * 单个费用规则的计算命令。
     *
     * @param labelAmount 当前动作标签金额和币种
     * @param rule 已冻结规则快照
     * @param tiers 已冻结阶梯列表
     * @param tierContext 当前动作前累计事实
     * @param roundingMode 费用版本冻结的舍入模式
     */
    public record FeeCalculationCommand(Money labelAmount,
                                        FeeRuleSnapshot rule,
                                        List<FeeTierSnapshot> tiers,
                                        TierContext tierContext,
                                        RoundingMode roundingMode) {

        public FeeCalculationCommand {
            Objects.requireNonNull(labelAmount, "label amount is required");
            requireNonNegative(labelAmount, "label amount");
            Objects.requireNonNull(rule, "fee rule is required");
            tiers = tiers == null ? List.of() : List.copyOf(tiers);
            Objects.requireNonNull(tierContext, "tier context is required");
            Objects.requireNonNull(roundingMode, "rounding mode is required");
        }
    }

    /**
     * 可持久化为清分明细的费用原子组件。
     *
     * @param componentType 百分比、固定费或限额调整
     * @param direction 对商户应结金额的借贷方向
     * @param amount 非负组件金额；百分比为标签币种，固定费为 USD
     * @param basisAmount 百分比基数；非百分比组件为空
     * @param percentageRate 百分比快照；非百分比组件为空
     * @param ruleId 来源规则主键
     * @param tierId 来源阶梯主键；标准规则为空
     */
    public record FeeComponent(FeeComponentType componentType,
                               EntryDirection direction,
                               Money amount,
                               Money basisAmount,
                               BigDecimal percentageRate,
                               Long ruleId,
                               Long tierId) {

        public FeeComponent {
            Objects.requireNonNull(componentType, "component type is required");
            Objects.requireNonNull(direction, "entry direction is required");
            Objects.requireNonNull(amount, "component amount is required");
            requireNonNegative(amount, "component amount");
            requireNonNegative(basisAmount, "component basis amount");
            Objects.requireNonNull(ruleId, "component rule id is required");
        }
    }

    /**
     * 单个费用组按实际组件币种保存的计算结果。
     *
     * @param matchedTierId 本笔结束时所在阶梯；标准规则为空
     * @param matchedTierIds 本笔实际使用的全部阶梯
     * @param components 按实际币种保存的费用组件，金额均为非负值
     * @param feeEvaluationStatus 费用组是否已得到单币种最终金额
     * @param limitEvaluationStatus 最低和最高费用是否已经求值
     * @param appliedLimit 已应用的同币种限额
     * @param finalFee 清分阶段可确定的最终费用；跨币种时为空
     * @param minimumFeeUsd USD 最低费用规则事实
     * @param maximumFeeUsd USD 最高费用规则事实
     */
    public record FeeCalculationResult(Long matchedTierId,
                                       List<Long> matchedTierIds,
                                       List<FeeComponent> components,
                                       FeeEvaluationStatus feeEvaluationStatus,
                                       LimitEvaluationStatus limitEvaluationStatus,
                                       AppliedLimit appliedLimit,
                                       Money finalFee,
                                       Money minimumFeeUsd,
                                       Money maximumFeeUsd) {

        public FeeCalculationResult {
            matchedTierIds = matchedTierIds == null ? List.of() : List.copyOf(matchedTierIds);
            components = components == null ? List.of() : List.copyOf(components);
            Objects.requireNonNull(feeEvaluationStatus, "fee evaluation status is required");
            Objects.requireNonNull(limitEvaluationStatus, "limit evaluation status is required");
            Objects.requireNonNull(appliedLimit, "applied limit is required");
            requireNonNegative(finalFee, "final fee");
            requireNonNegative(minimumFeeUsd, "minimum fee");
            requireNonNegative(maximumFeeUsd, "maximum fee");
        }
    }

    private static void requireNonNegative(Money money, String fieldName) {
        if (money != null && money.amount().signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }

    private static void requireUsd(Money money, String fieldName) {
        if (money != null && !"USD".equals(money.currency())) {
            throw new IllegalArgumentException(fieldName + " must use USD");
        }
    }

    private static BigDecimal requireNonNegative(BigDecimal amount, String fieldName) {
        Objects.requireNonNull(amount, fieldName + " is required");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
        return amount;
    }

}
