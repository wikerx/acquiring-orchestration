package com.scott.payment.finance.reserve.core;

import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveActionType;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveAdjustmentCommand;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveAdjustmentDirection;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveHoldCommand;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveReleaseCommand;
import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveReturnCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReserveCalculatorTest
 * @date : 2026-08-25 19:30
 * @email : scott_x@163.com
 * @description : 验证保证金按原标签币种计提和返还，并且累计返还不会突破原扣留金额。
 * @status : create
 */
class ReserveCalculatorTest {

    /** 支付成功后按标签金额和固化保证金比例生成原标签币种扣留事实。 */
    @Test
    void shouldHoldReserveInOriginalLabelCurrency() {
        System.out.println("保证金扣留：验证100 USD按10%生成10 USD HOLD明细");
        ReserveHoldCommand command = new ReserveHoldCommand(
                new Money(new BigDecimal("100"), "USD", 2),
                new BigDecimal("10"), RoundingMode.HALF_UP);

        var result = new ReserveCalculator().hold(command);

        assertThat(result.actionType()).isEqualTo(ReserveActionType.HOLD);
        assertThat(result.basisAmount().amount()).isEqualByComparingTo("100");
        assertThat(result.reserveRate()).isEqualByComparingTo("10");
        assertThat(result.amount().amount()).isEqualByComparingTo("10.00");
        assertThat(result.amount().currency()).isEqualTo("USD");
        assertThat(result.remainingAmount().amount()).isEqualByComparingTo("10.00");
    }

    /** 部分退款必须复用原支付保证金比例，并追加返还事实而非覆盖原扣留。 */
    @Test
    void shouldReturnReserveUsingOriginalPaymentRate() {
        System.out.println("保证金返还：验证退款20 USD复用原10%费率新增2 USD RETURN明细");
        ReserveReturnCommand command = new ReserveReturnCommand(
                amount("20"), amount("100"), amount("0"), new BigDecimal("10"),
                amount("10"), amount("0"), RoundingMode.HALF_UP);

        var result = new ReserveCalculator().returnReserve(command);

        assertThat(result.actionType()).isEqualTo(ReserveActionType.RETURN);
        assertThat(result.amount().amount()).isEqualByComparingTo("2.00");
        assertThat(result.amount().currency()).isEqualTo("USD");
        assertThat(result.remainingAmount().amount()).isEqualByComparingTo("8.00");
    }

    /** 最后一笔全额退款使用原扣留剩余额度兜底，消除多次组件舍入形成的尾差。 */
    @Test
    void shouldConsumeRemainingReserveOnFinalRefundWithoutExceedingHold() {
        System.out.println("保证金尾差：验证最后一笔退款返还剩余0.04 USD且累计不超过原扣留0.10 USD");
        ReserveReturnCommand command = new ReserveReturnCommand(
                amount("0.34"), amount("1.00"), amount("0.66"), new BigDecimal("10"),
                amount("0.10"), amount("0.06"), RoundingMode.HALF_UP);

        var result = new ReserveCalculator().returnReserve(command);

        assertThat(result.amount().amount()).isEqualByComparingTo("0.04");
        assertThat(result.remainingAmount().amount()).isZero();
        assertThat(new BigDecimal("0.06").add(result.amount().amount()))
                .isEqualByComparingTo("0.10");
    }

    /** 原扣留和累计已返保证金必须符合标签币种 exponent，确保剩余额度可真实落账。 */
    @Test
    void shouldRejectUnroundedReserveStateFacts() {
        System.out.println("保证金精度：验证0.105 USD不能作为已落账的原HOLD状态输入");

        assertThatThrownBy(() -> new ReserveReturnCommand(
                amount("0.10"), amount("1.00"), amount("0"), new BigDecimal("10"),
                new Money(new BigDecimal("0.105"), "USD", 2), amount("0"),
                RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reserve state facts");
    }

    /** 累计退款标签金额超过原支付时必须在纯计算入口拒绝。 */
    @Test
    void shouldRejectCumulativeRefundBeyondOriginalPayment() {
        assertThatThrownBy(() -> new ReserveReturnCommand(
                amount("20.01"), amount("100.00"), amount("80.00"), new BigDecimal("10"),
                amount("10.00"), amount("8.00"), RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cumulative refund");
    }

    /** 到期释放必须一次释放当前剩余标签币种保证金，不重新读取费率或执行换汇。 */
    @Test
    void shouldReleaseCurrentRemainingReserveInOriginalLabelCurrency() {
        System.out.println("保证金释放：验证到期后将剩余8 USD一次性生成RELEASE事实");

        var result = new ReserveCalculator().release(new ReserveReleaseCommand(
                amount("8.00"), new BigDecimal("10")));

        assertThat(result.actionType()).isEqualTo(ReserveActionType.RELEASE);
        assertThat(result.amount().amount()).isEqualByComparingTo("8.00");
        assertThat(result.amount().currency()).isEqualTo("USD");
        assertThat(result.remainingAmount().amount()).isZero();
    }

    /** 保证金贷记调整只能减少当前剩余负债，且不得突破剩余额度。 */
    @Test
    void shouldApplyCreditAdjustmentWithoutChangingCurrency() {
        System.out.println("保证金调整：验证CREDIT 1.25 USD将剩余保证金从8 USD降为6.75 USD");

        var result = new ReserveCalculator().adjust(new ReserveAdjustmentCommand(
                amount("8.00"), amount("1.25"), ReserveAdjustmentDirection.CREDIT,
                new BigDecimal("10")));

        assertThat(result.direction()).isEqualTo(ReserveAdjustmentDirection.CREDIT);
        assertThat(result.amount().amount()).isEqualByComparingTo("1.25");
        assertThat(result.remainingAmount().amount()).isEqualByComparingTo("6.75");
    }

    /** 保证金借记调整增加原标签币种负债，不允许传入其它币种。 */
    @Test
    void shouldApplyDebitAdjustmentInOriginalReserveCurrency() {
        System.out.println("保证金调整：验证DEBIT 1.25 USD将剩余保证金从8 USD增为9.25 USD");

        var result = new ReserveCalculator().adjust(new ReserveAdjustmentCommand(
                amount("8.00"), amount("1.25"), ReserveAdjustmentDirection.DEBIT,
                new BigDecimal("10")));

        assertThat(result.direction()).isEqualTo(ReserveAdjustmentDirection.DEBIT);
        assertThat(result.remainingAmount().amount()).isEqualByComparingTo("9.25");
    }

    /** 保证金贷记调整超过当前剩余负债时必须拒绝，避免剩余金额变为负数。 */
    @Test
    void shouldRejectCreditAdjustmentBeyondRemainingReserve() {
        assertThatThrownBy(() -> new ReserveAdjustmentCommand(
                amount("8.00"), amount("8.01"), ReserveAdjustmentDirection.CREDIT,
                new BigDecimal("10")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("credit adjustment");
    }

    private static Money amount(String value) {
        return new Money(new BigDecimal(value), "USD", 2);
    }
}
