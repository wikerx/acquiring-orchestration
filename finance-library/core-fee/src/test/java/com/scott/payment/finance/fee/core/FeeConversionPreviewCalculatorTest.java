package com.scott.payment.finance.fee.core;

import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeCalculationCommand;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeRuleSnapshot;
import com.scott.payment.finance.fee.model.FeeCalculationModels.TierContext;
import com.scott.payment.finance.fee.model.FeeConversionPreviewModels.FeeConversionPreviewCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import static com.scott.payment.finance.fee.model.FeeCalculationModels.AppliedLimit.MAXIMUM;
import static com.scott.payment.finance.fee.model.FeeCalculationModels.FeeEvaluationStatus.PENDING_SETTLEMENT_RATE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeConversionPreviewCalculatorTest
 * @date : 2026-08-25 19:40
 * @email : scott_x@163.com
 * @description : 验证后台预览可显式使用汇率统一求值跨币种费用，而原清分结果仍保持待结算汇率状态。
 * @status : create
 */
class FeeConversionPreviewCalculatorTest {

    /** Admin 预览转换标签币种百分比组件后应用 USD 限额，不修改清分组件事实。 */
    @Test
    void shouldEvaluateCrossCurrencyFeeForAdminPreviewOnly() {
        System.out.println("结算预览：验证4.60 EUR加1 USD换算为6.06 USD后应用5 USD上限");
        Money labelAmount = amount("200", "EUR", 2);
        FeeRuleSnapshot rule = new FeeRuleSnapshot(10L, FeeMode.STANDARD, new BigDecimal("2.3"),
                amount("1", "USD", 2), amount("1.5", "USD", 2), amount("5", "USD", 2),
                null);
        var componentResult = new FeeComponentCalculator().calculate(new FeeCalculationCommand(
                labelAmount, rule, List.of(), TierContext.empty(), RoundingMode.HALF_UP));

        var preview = new FeeConversionPreviewCalculator().calculate(new FeeConversionPreviewCommand(
                componentResult, "USD", 2, Map.of("EUR", new BigDecimal("1.10"), "USD", BigDecimal.ONE)));

        assertThat(componentResult.feeEvaluationStatus()).isEqualTo(PENDING_SETTLEMENT_RATE);
        assertThat(componentResult.finalFee()).isNull();
        assertThat(preview.rawFee().amount()).isEqualByComparingTo("6.06");
        assertThat(preview.finalFee().amount()).isEqualByComparingTo("5.00");
        assertThat(preview.finalFee().currency()).isEqualTo("USD");
        assertThat(preview.appliedLimit()).isEqualTo(MAXIMUM);
    }

    /** 标签币种百分比组件转换为 USD 预览时，必须显式提供标签币种到 USD 的直接汇率。 */
    @Test
    void shouldRejectPreviewWithoutLabelToUsdRate() {
        System.out.println("费用预览汇率：验证EUR百分比费用缺少EUR到USD直接汇率时拒绝求值");
        Money labelAmount = amount("100", "EUR", 2);
        FeeRuleSnapshot rule = new FeeRuleSnapshot(11L, FeeMode.STANDARD, BigDecimal.ONE,
                amount("1", "USD", 2), null, null,
                null);
        var componentResult = new FeeComponentCalculator().calculate(new FeeCalculationCommand(
                labelAmount, rule, List.of(), TierContext.empty(), RoundingMode.HALF_UP));
        FeeConversionPreviewCommand command = new FeeConversionPreviewCommand(
                componentResult, "USD", 2, Map.of());

        assertThatThrownBy(() -> new FeeConversionPreviewCalculator().calculate(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("direct preview rate is missing");
    }

    private static Money amount(String value, String currency, int exponent) {
        return new Money(new BigDecimal(value), currency, exponent);
    }
}
