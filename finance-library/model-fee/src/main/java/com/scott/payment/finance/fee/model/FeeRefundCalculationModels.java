package com.scott.payment.finance.fee.model;

import com.scott.payment.finance.fee.model.FeeCalculationModels.EntryDirection;
import com.scott.payment.finance.money.model.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeRefundCalculationModels
 * @date : 2026-08-25 19:35
 * @email : scott_x@163.com
 * @description : 定义退款时按原实际收费组件返还费用的不可变契约，各组件保持原收费事实币种且不执行汇率换算。
 * @status : create
 */
public final class FeeRefundCalculationModels {

    private FeeRefundCalculationModels() {
    }

    /** 原交易费用返还策略。 */
    public enum FeeRefundPolicy {
        /**
         * NONE 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        NONE,
        /**
         * FULL 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        FULL,
        PROPORTIONAL
    }

    /**
     * 可返还的原实际收费组件。
     *
     * @param sourceComponentNo 原清分或结算结果组件唯一编号
     * @param originalChargedAmount 原组件实际向商户收取的金额
     * @param refundedAmountBefore 本次动作前该组件累计已返金额
     */
    public record RefundableFeeComponent(String sourceComponentNo,
                                         Money originalChargedAmount,
                                         Money refundedAmountBefore) {

        public RefundableFeeComponent {
            if (sourceComponentNo == null || sourceComponentNo.isBlank()) {
                throw new IllegalArgumentException("source component number is required");
            }
            Objects.requireNonNull(originalChargedAmount, "original charged amount is required");
            Objects.requireNonNull(refundedAmountBefore, "refunded amount before is required");
            requireNonNegative(originalChargedAmount, "original charged amount");
            requireNonNegative(refundedAmountBefore, "refunded amount before");
            if (!originalChargedAmount.sameCurrency(refundedAmountBefore)) {
                throw new IllegalArgumentException("refunded fee must use source component currency and exponent");
            }
            if (!conformsToExponent(originalChargedAmount) || !conformsToExponent(refundedAmountBefore)) {
                throw new IllegalArgumentException("source fee facts must conform to source currency exponent");
            }
            if (refundedAmountBefore.amount().compareTo(originalChargedAmount.amount()) > 0) {
                throw new IllegalArgumentException("refunded fee must not exceed original charged amount");
            }
        }
    }

    /**
     * 费用返还计算命令。
     *
     * @param policy 原支付动作固化的费用返还策略
     * @param refundLabelAmount 本次成功退款标签金额
     * @param originalLabelAmount 原支付成功标签金额
     * @param refundedLabelAmountBefore 本次退款前累计成功退款标签金额
     * @param sourceComponents 可返还的原实际收费组件
     * @param roundingMode 原支付费用版本固化的舍入模式
     */
    public record FeeRefundCommand(FeeRefundPolicy policy,
                                   Money refundLabelAmount,
                                   Money originalLabelAmount,
                                   Money refundedLabelAmountBefore,
                                   List<RefundableFeeComponent> sourceComponents,
                                   RoundingMode roundingMode) {

        public FeeRefundCommand {
            Objects.requireNonNull(policy, "fee refund policy is required");
            Objects.requireNonNull(refundLabelAmount, "refund label amount is required");
            Objects.requireNonNull(originalLabelAmount, "original label amount is required");
            Objects.requireNonNull(refundedLabelAmountBefore, "refunded label amount before is required");
            requireNonNegative(refundLabelAmount, "refund label amount");
            requireNonNegative(originalLabelAmount, "original label amount");
            requireNonNegative(refundedLabelAmountBefore, "refunded label amount before");
            sourceComponents = sourceComponents == null ? List.of() : List.copyOf(sourceComponents);
            Objects.requireNonNull(roundingMode, "rounding mode is required");
            Set<String> sourceComponentNumbers = new HashSet<>();
            for (RefundableFeeComponent sourceComponent : sourceComponents) {
                if (!sourceComponentNumbers.add(sourceComponent.sourceComponentNo())) {
                    throw new IllegalArgumentException("duplicate source fee component is not allowed");
                }
            }
            if (!originalLabelAmount.sameCurrency(refundLabelAmount)
                    || !originalLabelAmount.sameCurrency(refundedLabelAmountBefore)) {
                throw new IllegalArgumentException("refund amounts must use original label currency and exponent");
            }
            if (originalLabelAmount.amount().signum() <= 0) {
                throw new IllegalArgumentException("original label amount must be positive");
            }
            if (refundedLabelAmountBefore.amount().add(refundLabelAmount.amount())
                    .compareTo(originalLabelAmount.amount()) > 0) {
                throw new IllegalArgumentException("cumulative refund must not exceed original label amount");
            }
        }
    }

    /**
     * 新增的单条费用返还组件。
     *
     * @param sourceComponentNo 被返还的原收费组件编号
     * @param direction 对商户应结金额的方向，费用返还固定为 CREDIT
     * @param amount 本次返还的原组件币种绝对值金额
     * @param remainingRefundableAmount 本次返还后的原组件剩余可返金额
     */
    public record FeeRefundComponent(String sourceComponentNo,
                                     EntryDirection direction,
                                     Money amount,
                                     Money remainingRefundableAmount) {

        public FeeRefundComponent {
            if (sourceComponentNo == null || sourceComponentNo.isBlank()) {
                throw new IllegalArgumentException("source component number is required");
            }
            if (direction != EntryDirection.CREDIT) {
                throw new IllegalArgumentException("fee refund direction must be credit");
            }
            Objects.requireNonNull(amount, "fee refund amount is required");
            Objects.requireNonNull(remainingRefundableAmount, "remaining refundable amount is required");
            requireNonNegative(amount, "fee refund amount");
            requireNonNegative(remainingRefundableAmount, "remaining refundable amount");
            if (!amount.sameCurrency(remainingRefundableAmount)) {
                throw new IllegalArgumentException("fee refund result must keep source currency and exponent");
            }
        }
    }

    /**
     * 费用返还计算结果。
     *
     * @param policy 实际使用的固化返还策略
     * @param appliedRatio 本次应用于原收费组件的比例
     * @param components 应追加保存的原收费事实币种费用返还组件
     */
    public record FeeRefundResult(FeeRefundPolicy policy,
                                  BigDecimal appliedRatio,
                                  List<FeeRefundComponent> components) {

        public FeeRefundResult {
            Objects.requireNonNull(policy, "fee refund policy is required");
            Objects.requireNonNull(appliedRatio, "applied ratio is required");
            if (appliedRatio.signum() < 0) {
                throw new IllegalArgumentException("applied ratio must not be negative");
            }
            components = components == null ? List.of() : List.copyOf(components);
        }
    }

    private static boolean conformsToExponent(Money amount) {
        return amount.amount().stripTrailingZeros().scale() <= amount.exponent();
    }

    private static void requireNonNegative(Money money, String fieldName) {
        if (money.amount().signum() < 0) {
            throw new IllegalArgumentException(fieldName + " must not be negative");
        }
    }
}
