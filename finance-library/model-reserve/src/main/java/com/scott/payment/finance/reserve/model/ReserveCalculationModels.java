package com.scott.payment.finance.reserve.model;

import com.scott.payment.finance.money.model.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReserveCalculationModels
 * @date : 2026-08-25 19:30
 * @email : scott_x@163.com
 * @description : 定义标签币种保证金扣留、返还、到期释放和差额调整的不可变计算契约，不包含汇率、结算币种或资金账户状态。
 * @status : create
 */
public final class ReserveCalculationModels {

    private ReserveCalculationModels() {
    }

    /** 保证金清分事实类型。 */
    public enum ReserveActionType {
        HOLD,
        RETURN,
        RELEASE,
        ADJUSTMENT
    }

    /** 保证金差额调整方向；DEBIT增加商户保证金负债，CREDIT减少商户保证金负债。 */
    public enum ReserveAdjustmentDirection {
        DEBIT,
        CREDIT
    }

    /**
     * 保证金扣留计算命令。
     *
     * @param labelAmount 当前成功支付或请款的标签金额
     * @param reserveRate 已冻结的保证金百分比，10 表示 10%
     * @param roundingMode 原费用版本冻结的舍入模式
     */
    public record ReserveHoldCommand(Money labelAmount,
                                     BigDecimal reserveRate,
                                     RoundingMode roundingMode) {

        public ReserveHoldCommand {
            Objects.requireNonNull(labelAmount, "label amount is required");
            requireNonNegative(labelAmount, "label amount");
            validateRate(reserveRate);
            Objects.requireNonNull(roundingMode, "rounding mode is required");
        }
    }

    /**
     * 保证金退款返还计算命令。
     *
     * @param refundLabelAmount 本次成功退款的标签金额
     * @param originalLabelAmount 原支付成功标签金额
     * @param refundedLabelAmountBefore 本次退款前已成功退款的标签金额
     * @param originalReserveRate 原支付动作固化的保证金比例
     * @param originalHoldAmount 原支付实际扣留的保证金金额
     * @param returnedReserveAmountBefore 本次退款前已累计返还的保证金金额
     * @param roundingMode 原支付费用版本固化的舍入模式
     */
    public record ReserveReturnCommand(Money refundLabelAmount,
                                       Money originalLabelAmount,
                                       Money refundedLabelAmountBefore,
                                       BigDecimal originalReserveRate,
                                       Money originalHoldAmount,
                                       Money returnedReserveAmountBefore,
                                       RoundingMode roundingMode) {

        public ReserveReturnCommand {
            Objects.requireNonNull(refundLabelAmount, "refund label amount is required");
            Objects.requireNonNull(originalLabelAmount, "original label amount is required");
            Objects.requireNonNull(refundedLabelAmountBefore, "refunded label amount before is required");
            validateRate(originalReserveRate);
            Objects.requireNonNull(originalHoldAmount, "original hold amount is required");
            Objects.requireNonNull(returnedReserveAmountBefore, "returned reserve amount before is required");
            Objects.requireNonNull(roundingMode, "rounding mode is required");
            requireNonNegative(refundLabelAmount, "refund label amount");
            requireNonNegative(originalLabelAmount, "original label amount");
            requireNonNegative(refundedLabelAmountBefore, "refunded label amount before");
            requireNonNegative(originalHoldAmount, "original hold amount");
            requireNonNegative(returnedReserveAmountBefore, "returned reserve amount before");
            requireSameCurrency(originalLabelAmount, refundLabelAmount);
            requireSameCurrency(originalLabelAmount, refundedLabelAmountBefore);
            requireSameCurrency(originalLabelAmount, originalHoldAmount);
            requireSameCurrency(originalLabelAmount, returnedReserveAmountBefore);
            if (!conformsToExponent(originalHoldAmount) || !conformsToExponent(returnedReserveAmountBefore)) {
                throw new IllegalArgumentException("reserve state facts must conform to original label exponent");
            }
            if (refundedLabelAmountBefore.amount().add(refundLabelAmount.amount())
                    .compareTo(originalLabelAmount.amount()) > 0) {
                throw new IllegalArgumentException("cumulative refund must not exceed original label amount");
            }
            if (returnedReserveAmountBefore.amount().compareTo(originalHoldAmount.amount()) > 0) {
                throw new IllegalArgumentException("returned reserve must not exceed original hold amount");
            }
        }
    }

    /**
     * 保证金到期释放计算命令。
     *
     * @param currentRemainingAmount 到期时数据库锁定的剩余标签币种保证金
     * @param originalReserveRate 原支付动作冻结的保证金比例，仅用于审计结果
     */
    public record ReserveReleaseCommand(Money currentRemainingAmount,
                                        BigDecimal originalReserveRate) {

        public ReserveReleaseCommand {
            Objects.requireNonNull(currentRemainingAmount, "current remaining reserve is required");
            validateRate(originalReserveRate);
            requirePositive(currentRemainingAmount, "current remaining reserve");
            if (!conformsToExponent(currentRemainingAmount)) {
                throw new IllegalArgumentException(
                        "current remaining reserve must conform to original label exponent");
            }
        }
    }

    /**
     * 保证金差额调整计算命令。
     *
     * @param currentRemainingAmount 调整前数据库锁定的剩余标签币种保证金
     * @param adjustmentAmount 本次经复核的标签币种调整绝对金额
     * @param direction DEBIT增加负债，CREDIT减少负债
     * @param originalReserveRate 原支付动作冻结的保证金比例，仅用于审计结果
     */
    public record ReserveAdjustmentCommand(Money currentRemainingAmount,
                                           Money adjustmentAmount,
                                           ReserveAdjustmentDirection direction,
                                           BigDecimal originalReserveRate) {

        public ReserveAdjustmentCommand {
            Objects.requireNonNull(currentRemainingAmount, "current remaining reserve is required");
            Objects.requireNonNull(adjustmentAmount, "reserve adjustment amount is required");
            Objects.requireNonNull(direction, "reserve adjustment direction is required");
            validateRate(originalReserveRate);
            requireNonNegative(currentRemainingAmount, "current remaining reserve");
            requirePositive(adjustmentAmount, "reserve adjustment amount");
            requireSameCurrency(currentRemainingAmount, adjustmentAmount);
            if (!conformsToExponent(currentRemainingAmount) || !conformsToExponent(adjustmentAmount)) {
                throw new IllegalArgumentException(
                        "reserve adjustment facts must conform to original label exponent");
            }
            if (direction == ReserveAdjustmentDirection.CREDIT
                    && adjustmentAmount.amount().compareTo(currentRemainingAmount.amount()) > 0) {
                throw new IllegalArgumentException(
                        "credit adjustment must not exceed current remaining reserve");
            }
        }
    }

    /**
     * 单条保证金清分计算结果。
     *
     * @param actionType 扣留或返还
     * @param basisAmount 本次使用的标签币种计算基数
     * @param reserveRate 使用的原支付固化保证金比例
     * @param amount 本次保证金事实的绝对值金额
     * @param remainingAmount 应用本次事实后的原币种剩余扣留金额
     */
    public record ReserveCalculationResult(ReserveActionType actionType,
                                           Money basisAmount,
                                           BigDecimal reserveRate,
                                           Money amount,
                                           Money remainingAmount) {

        public ReserveCalculationResult {
            Objects.requireNonNull(actionType, "reserve action type is required");
            Objects.requireNonNull(basisAmount, "reserve basis amount is required");
            validateRate(reserveRate);
            Objects.requireNonNull(amount, "reserve amount is required");
            Objects.requireNonNull(remainingAmount, "remaining reserve amount is required");
            requireNonNegative(basisAmount, "reserve basis amount");
            requireNonNegative(amount, "reserve amount");
            requireNonNegative(remainingAmount, "remaining reserve amount");
        }
    }

    /**
     * 单条保证金差额调整计算结果。
     *
     * @param direction 调整方向
     * @param basisAmount 调整前剩余标签币种保证金
     * @param reserveRate 原支付冻结保证金比例
     * @param amount 调整绝对金额
     * @param remainingAmount 调整后的剩余标签币种保证金
     */
    public record ReserveAdjustmentResult(ReserveAdjustmentDirection direction,
                                          Money basisAmount,
                                          BigDecimal reserveRate,
                                          Money amount,
                                          Money remainingAmount) {

        public ReserveAdjustmentResult {
            Objects.requireNonNull(direction, "reserve adjustment direction is required");
            Objects.requireNonNull(basisAmount, "reserve adjustment basis is required");
            validateRate(reserveRate);
            Objects.requireNonNull(amount, "reserve adjustment amount is required");
            Objects.requireNonNull(remainingAmount, "remaining reserve amount is required");
            requireNonNegative(basisAmount, "reserve adjustment basis");
            requirePositive(amount, "reserve adjustment amount");
            requireNonNegative(remainingAmount, "remaining reserve amount");
            requireSameCurrency(basisAmount, amount);
            requireSameCurrency(basisAmount, remainingAmount);
        }
    }

    private static void validateRate(BigDecimal reserveRate) {
        Objects.requireNonNull(reserveRate, "reserve rate is required");
        if (reserveRate.signum() < 0 || reserveRate.compareTo(new BigDecimal("100")) > 0) {
            throw new IllegalArgumentException("reserve rate must be between 0 and 100");
        }
    }

    private static void requireSameCurrency(Money expected, Money actual) {
        if (!expected.sameCurrency(actual)) {
            throw new IllegalArgumentException("reserve amounts must use original label currency and exponent");
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

    private static void requirePositive(Money money, String fieldName) {
        if (money.amount().signum() <= 0) {
            throw new IllegalArgumentException(fieldName + " must be positive");
        }
    }
}
