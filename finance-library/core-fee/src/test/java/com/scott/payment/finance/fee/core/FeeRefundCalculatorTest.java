package com.scott.payment.finance.fee.core;

import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.FeeRefundCommand;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.FeeRefundPolicy;
import com.scott.payment.finance.fee.model.FeeRefundCalculationModels.RefundableFeeComponent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static com.scott.payment.finance.fee.model.FeeCalculationModels.EntryDirection.CREDIT;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeRefundCalculatorTest
 * @date : 2026-08-25 19:35
 * @email : scott_x@163.com
 * @description : 验证原实际收费按 NONE、FULL、PROPORTIONAL 策略逐币种返还，并受每个源组件剩余额度约束。
 * @status : create
 */
class FeeRefundCalculatorTest {

    /** NONE 策略不因退款动作产生任何原交易费用返还。 */
    @Test
    void shouldNotReturnFeeWhenPolicyIsNone() {
        System.out.println("费用返还：验证NONE策略不生成任何FEE_REVERSAL组件");
        FeeRefundCommand command = new FeeRefundCommand(FeeRefundPolicy.NONE,
                usd("20"), usd("100"), usd("0"), List.of(), RoundingMode.HALF_UP);

        var result = new FeeRefundCalculator().calculate(command);

        assertThat(result.policy()).isEqualTo(FeeRefundPolicy.NONE);
        assertThat(result.appliedRatio()).isZero();
        assertThat(result.components()).isEmpty();
    }

    /** FULL 策略逐组件返还全部剩余额度，并保持每个原收费组件的币种和 exponent。 */
    @Test
    void shouldReturnEveryComponentRemainingAmountWhenPolicyIsFull() {
        System.out.println("费用返还：验证FULL策略分别返还USD和JPY源组件的剩余额度");
        FeeRefundCommand command = new FeeRefundCommand(FeeRefundPolicy.FULL,
                usd("20"), usd("100"), usd("0"), List.of(
                new RefundableFeeComponent("FEE-USD", usd("2.60"), usd("0.50")),
                new RefundableFeeComponent("FEE-JPY", jpy("100"), jpy("30"))
        ), RoundingMode.HALF_UP);

        var result = new FeeRefundCalculator().calculate(command);

        assertThat(result.appliedRatio()).isEqualByComparingTo("1");
        assertThat(result.components()).hasSize(2);
        assertThat(result.components().get(0).sourceComponentNo()).isEqualTo("FEE-USD");
        assertThat(result.components().get(0).direction()).isEqualTo(CREDIT);
        assertThat(result.components().get(0).amount().amount()).isEqualByComparingTo("2.10");
        assertThat(result.components().get(0).remainingRefundableAmount().amount()).isZero();
        assertThat(result.components().get(1).amount().amount()).isEqualByComparingTo("70");
        assertThat(result.components().get(1).amount().exponent()).isZero();
    }

    /** PROPORTIONAL 策略以本次退款占原支付比例逐个计算原实际收费组件。 */
    @Test
    void shouldReturnProportionalAmountForEachSourceCurrency() {
        System.out.println("费用返还：验证20/100退款比例分别作用于2.60 USD和100 JPY实际收费");
        FeeRefundCommand command = new FeeRefundCommand(FeeRefundPolicy.PROPORTIONAL,
                usd("20"), usd("100"), usd("0"), List.of(
                new RefundableFeeComponent("FEE-USD", usd("2.60"), usd("0")),
                new RefundableFeeComponent("FEE-JPY", jpy("100"), jpy("0"))
        ), RoundingMode.HALF_UP);

        var result = new FeeRefundCalculator().calculate(command);

        assertThat(result.appliedRatio()).isEqualByComparingTo("0.2");
        assertThat(result.components()).hasSize(2);
        assertThat(result.components().get(0).amount().amount()).isEqualByComparingTo("0.52");
        assertThat(result.components().get(0).remainingRefundableAmount().amount())
                .isEqualByComparingTo("2.08");
        assertThat(result.components().get(1).amount().amount()).isEqualByComparingTo("20");
        assertThat(result.components().get(1).remainingRefundableAmount().amount())
                .isEqualByComparingTo("80");
    }

    /** 比例返费最后一笔使用源组件剩余额度兜底，且累计返还不能突破原实际收费。 */
    @Test
    void shouldConsumeEachComponentRemainingCapOnFinalRefund() {
        System.out.println("费用返还尾差：验证最后一笔全额退款返完源组件剩余0.04 USD");
        FeeRefundCommand command = new FeeRefundCommand(FeeRefundPolicy.PROPORTIONAL,
                usd("0.34"), usd("1.00"), usd("0.66"), List.of(
                new RefundableFeeComponent("FEE-USD", usd("0.10"), usd("0.06"))
        ), RoundingMode.HALF_UP);

        var result = new FeeRefundCalculator().calculate(command);

        assertThat(result.components()).singleElement().satisfies(component -> {
            assertThat(component.amount().amount()).isEqualByComparingTo("0.04");
            assertThat(component.remainingRefundableAmount().amount()).isZero();
            assertThat(new BigDecimal("0.06").add(component.amount().amount()))
                    .isEqualByComparingTo("0.10");
        });
    }

    /** 同一个原收费组件不得在一次返费命令中重复出现，避免生成重复资金事实。 */
    @Test
    void shouldRejectDuplicateSourceComponentNumbers() {
        System.out.println("费用返还幂等：验证同一源组件编号重复输入时在计算前拒绝");

        assertThatThrownBy(() -> new FeeRefundCommand(FeeRefundPolicy.FULL,
                usd("20"), usd("100"), usd("0"), List.of(
                new RefundableFeeComponent("FEE-USD", usd("2.60"), usd("0")),
                new RefundableFeeComponent("FEE-USD", usd("2.60"), usd("0"))
        ), RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate source fee component");
    }

    /** 原实际收费和历史已返金额必须已经符合源币种 exponent，避免返还舍入后突破原金额。 */
    @Test
    void shouldRejectUnroundedSourceFeeFacts() {
        System.out.println("费用返还精度：验证0.105 USD不能作为已落账的原实际收费输入");

        assertThatThrownBy(() -> new RefundableFeeComponent("FEE-USD",
                new Money(new BigDecimal("0.105"), "USD", 2), usd("0")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source currency exponent");
    }

    private static Money usd(String value) {
        return new Money(new BigDecimal(value), "USD", 2);
    }

    private static Money jpy(String value) {
        return new Money(new BigDecimal(value), "JPY", 0);
    }
}
