package com.scott.payment.finance.reserve.core;

import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveCalculationResult;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveAdjustmentCommand;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveAdjustmentDirection;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveAdjustmentResult;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveHoldCommand;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveReleaseCommand;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveReturnCommand;

import java.math.BigDecimal;
import java.math.MathContext;

import static com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveActionType.HOLD;
import static com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveActionType.RELEASE;
import static com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveActionType.RETURN;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReserveCalculator
 * @date : 2026-08-25 19:30
 * @email : scott_x@163.com
 * @description : 仅按原标签币种计算保证金扣留、返还、到期释放和差额调整事实，使用固化费率且不执行任何汇率换算。
 * @status : create
 */
public class ReserveCalculator {

    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final MathContext CALCULATION_CONTEXT = MathContext.DECIMAL128;

    /**
     * 按当前成功动作的标签金额计算保证金扣留。
     *
     * @param command 标签金额、固化保证金比例和舍入模式
     * @return 标签币种扣留事实及扣留后的剩余金额
     */
    public ReserveCalculationResult hold(ReserveHoldCommand command) {
        Money amount = percentage(command.labelAmount(), command.reserveRate(), command.roundingMode());
        return new ReserveCalculationResult(HOLD, command.labelAmount(), command.reserveRate(), amount, amount);
    }

    /**
     * 使用原支付固化比例计算本次退款应返保证金，并以原扣留剩余金额封顶。
     *
     * @param command 本次退款、原支付和累计返还事实
     * @return 新增的标签币种返还事实及返还后的剩余扣留金额
     */
    public ReserveCalculationResult returnReserve(ReserveReturnCommand command) {
        BigDecimal remaining = command.originalHoldAmount().amount()
                .subtract(command.returnedReserveAmountBefore().amount(), CALCULATION_CONTEXT);
        boolean finalRefund = command.refundedLabelAmountBefore().amount()
                .add(command.refundLabelAmount().amount(), CALCULATION_CONTEXT)
                .compareTo(command.originalLabelAmount().amount()) == 0;
        Money calculated = percentage(command.refundLabelAmount(), command.originalReserveRate(),
                command.roundingMode());
        BigDecimal returned = finalRefund ? remaining : calculated.amount().min(remaining);
        Money returnAmount = new Money(returned, command.originalHoldAmount().currency(),
                command.originalHoldAmount().exponent()).rounded(command.roundingMode());
        Money remainingAmount = new Money(remaining.subtract(returnAmount.amount(),
                CALCULATION_CONTEXT), command.originalHoldAmount().currency(),
                command.originalHoldAmount().exponent()).rounded(command.roundingMode());
        return new ReserveCalculationResult(RETURN, command.refundLabelAmount(), command.originalReserveRate(),
                returnAmount, remainingAmount);
    }

    /**
     * 将到期时数据库锁定的剩余保证金一次性释放，避免重新按比例计算产生尾差。
     *
     * @param command 当前剩余标签币种保证金和原冻结比例
     * @return RELEASE事实，剩余金额固定为零
     */
    public ReserveCalculationResult release(ReserveReleaseCommand command) {
        Money released = command.currentRemainingAmount();
        Money remaining = new Money(BigDecimal.ZERO, released.currency(), released.exponent());
        return new ReserveCalculationResult(RELEASE, released, command.originalReserveRate(),
                released, remaining);
    }

    /**
     * 按经复核方向调整当前标签币种保证金负债；DEBIT增加，CREDIT减少。
     *
     * @param command 当前剩余金额、调整绝对金额、方向和原冻结比例
     * @return 调整方向、金额和调整后剩余保证金
     */
    public ReserveAdjustmentResult adjust(ReserveAdjustmentCommand command) {
        BigDecimal remaining = command.direction() == ReserveAdjustmentDirection.DEBIT
                ? command.currentRemainingAmount().amount().add(
                        command.adjustmentAmount().amount(), CALCULATION_CONTEXT)
                : command.currentRemainingAmount().amount().subtract(
                        command.adjustmentAmount().amount(), CALCULATION_CONTEXT);
        Money remainingAmount = new Money(remaining, command.currentRemainingAmount().currency(),
                command.currentRemainingAmount().exponent());
        return new ReserveAdjustmentResult(command.direction(), command.currentRemainingAmount(),
                command.originalReserveRate(), command.adjustmentAmount(), remainingAmount);
    }

    private Money percentage(Money basis,
                                      BigDecimal rate,
                                      java.math.RoundingMode roundingMode) {
        BigDecimal amount = basis.amount()
                .multiply(rate, CALCULATION_CONTEXT)
                .divide(ONE_HUNDRED, CALCULATION_CONTEXT);
        return new Money(amount, basis.currency(), basis.exponent()).rounded(roundingMode);
    }
}
