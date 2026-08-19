package com.scott.payment.admin.service.impl;

import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationRequest;
import com.scott.payment.admin.dto.fee.AdminFeeDTOs.FeeSimulationResponse;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeRuleDO;
import com.scott.payment.admin.entity.fee.FeeEntities.FeeRuleTierDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminFeeSimulationCalculatorTests
 * @date : 2026-08-18 00:00
 * @email : scott_x@163.com
 * @description : 费用试算纯计算测试，验证标签币种百分比、USD 固定费用、上下限和月累计阶梯规则。
 * @status : create
 */
class AdminFeeSimulationCalculatorTests {

    /** 标签币种百分比费用转换为 USD 后，与固定费用合并并应用最高限额。 */
    @Test
    void shouldCombinePercentageAndFixedFeeThenApplyUsdCap() {
        System.out.println("费用试算：验证标签币种百分比转换、USD固定费用及最高限额");
        FeeRuleDO rule = standardRule("2.3", "1", "1.5", "5");
        FeeSimulationRequest request = request("200", "EUR", "1.10");

        FeeSimulationResponse response = new AdminFeeSimulationCalculator().calculate(
                request, rule, List.of(), new BigDecimal("1.10"));

        assertThat(response.getPercentageFeeLabel()).isEqualByComparingTo("4.6");
        assertThat(response.getRawFeeUsd()).isEqualByComparingTo("6.06");
        assertThat(response.getFinalFeeUsd()).isEqualByComparingTo("5");
        assertThat(response.getAppliedLimit()).isEqualTo("MAXIMUM");
    }

    /** 月累计笔数应包含当前交易，并按达到的档位对当前整笔交易计费。 */
    @Test
    void shouldSelectCountTierAfterIncludingCurrentTransaction() {
        System.out.println("阶梯试算：验证当前交易计入月累计笔数后按整笔落档");
        FeeRuleDO rule = standardRule("0", "0", null, null);
        rule.setFeeMode("TIER");
        rule.setTierMetric("COUNT");
        rule.setTierPeriod("MONTH");
        FeeRuleTierDO first = tier(1L, "0", "100", "2.5", "0");
        FeeRuleTierDO second = tier(2L, "100", null, "2.0", "0");
        FeeSimulationRequest request = request("100", "USD", "1");
        request.setMonthlyCountBefore(99L);

        FeeSimulationResponse response = new AdminFeeSimulationCalculator().calculate(
                request, rule, List.of(first, second), BigDecimal.ONE);

        assertThat(response.getMatchedTierId()).isEqualTo(2L);
        assertThat(response.getFinalFeeUsd()).isEqualByComparingTo("2");
    }

    /** 试算只接受标签币种到 USD 的直接汇率，零值或负值必须拒绝。 */
    @Test
    void shouldRejectMissingDirectRate() {
        System.out.println("汇率校验：验证标签币种到USD的直接汇率缺失时拒绝试算");
        FeeSimulationRequest request = request("100", "EUR", "0");

        assertThatThrownBy(() -> new AdminFeeSimulationCalculator().calculate(
                request, standardRule("2", "0", null, null), List.of(), BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("direct rate");
    }

    /** 标签币种本身为 USD 时只能使用恒等汇率，避免错误放大或缩小百分比费用。 */
    @Test
    void shouldRejectNonIdentityUsdRate() {
        System.out.println("USD汇率校验：验证USD标签币种只能使用1作为直接汇率");
        FeeSimulationRequest request = request("100", "USD", "1.1");

        assertThatThrownBy(() -> new AdminFeeSimulationCalculator().calculate(
                request, standardRule("2", "0", null, null), List.of(), new BigDecimal("1.1")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("USD direct rate must be 1");
    }

    private static FeeRuleDO standardRule(String percentage, String fixed, String minimum, String maximum) {
        FeeRuleDO rule = new FeeRuleDO();
        rule.setId(10L);
        rule.setFeeMode("STANDARD");
        rule.setPercentageRate(new BigDecimal(percentage));
        rule.setFixedAmountUsd(new BigDecimal(fixed));
        rule.setMinimumAmountUsd(minimum == null ? null : new BigDecimal(minimum));
        rule.setMaximumAmountUsd(maximum == null ? null : new BigDecimal(maximum));
        return rule;
    }

    private static FeeRuleTierDO tier(Long id, String lower, String upper, String percentage, String fixed) {
        FeeRuleTierDO tier = new FeeRuleTierDO();
        tier.setId(id);
        tier.setLowerBound(new BigDecimal(lower));
        tier.setUpperBound(upper == null ? null : new BigDecimal(upper));
        tier.setPercentageRate(new BigDecimal(percentage));
        tier.setFixedAmountUsd(new BigDecimal(fixed));
        return tier;
    }

    private static FeeSimulationRequest request(String amount, String currency, String directRate) {
        FeeSimulationRequest request = new FeeSimulationRequest();
        request.setLabelAmount(new BigDecimal(amount));
        request.setLabelCurrency(currency);
        request.setMonthlyCountBefore(0L);
        request.setMonthlyAmountUsdBefore(BigDecimal.ZERO);
        return request;
    }
}
