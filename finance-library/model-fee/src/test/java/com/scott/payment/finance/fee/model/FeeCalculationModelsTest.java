package com.scott.payment.finance.fee.model;

import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeCalculationCommand;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeRuleSnapshot;
import com.scott.payment.finance.fee.model.FeeCalculationModels.TierContext;
import com.scott.payment.finance.money.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证通用有符号金额进入费用领域后仍受费用基数非负约束。
 */
class FeeCalculationModelsTest {

    /**
     * 费用计算基数不得使用通用 Money 可表达的负净额。
     */
    @Test
    void shouldRejectNegativeFeeLabelAmount() {
        FeeRuleSnapshot rule = new FeeRuleSnapshot(1L, FeeMode.STANDARD, BigDecimal.ONE,
                null, null, null, null);

        assertThatThrownBy(() -> new FeeCalculationCommand(
                new Money(new BigDecimal("-1"), "USD", 2), rule, List.of(),
                TierContext.empty(), RoundingMode.HALF_UP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("label amount must not be negative");
    }

    /**
     * 固定费、最低费和最高费必须沿用商户配置的 USD 口径，不能扩展成任意币种。
     */
    @Test
    void shouldRejectNonUsdFixedAndLimitAmounts() {

        assertThatThrownBy(() -> new FeeRuleSnapshot(
                2L,
                FeeMode.STANDARD,
                BigDecimal.ONE,
                new Money(new BigDecimal("0.30"), "EUR", 2),
                null,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixed fee must use USD");
    }

    /**
     * 阶梯语义沿用现有商户配置，TIER 规则必须声明 COUNT 或 AMOUNT。
     */
    @Test
    void shouldRequireMetricForTierRuleWithoutAddingNewPricingMode() {
        assertThatThrownBy(() -> new FeeRuleSnapshot(
                3L, FeeMode.TIER, BigDecimal.ONE, null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tier fee rule requires tier metric");
    }
}
