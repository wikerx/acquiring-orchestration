package com.scott.payment.finance.fee.core;

import com.scott.payment.finance.money.model.Money;
import com.scott.payment.finance.fee.model.FeeCalculationModels.AppliedLimit;
import com.scott.payment.finance.fee.model.FeeCalculationModels.EntryDirection;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeCalculationCommand;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeCalculationResult;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeComponentType;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeEvaluationStatus;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeRuleSnapshot;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeTierSnapshot;
import com.scott.payment.finance.fee.model.FeeCalculationModels.LimitEvaluationStatus;
import com.scott.payment.finance.fee.model.FeeCalculationModels.TierContext;
import com.scott.payment.finance.fee.model.FeeCalculationModels.TierMetric;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeComponentCalculatorTest
 * @date : 2026-08-25 19:20
 * @email : scott_x@163.com
 * @description : 验证生产清分与后台试算按商户配置生成标签币种百分比和 USD 固定费用组件，禁止隐式换汇。
 * @status : create
 */
class FeeComponentCalculatorTest {

    /** 同币种百分比、固定费和限额应在清分阶段形成可直接结算的最终费用。 */
    @Test
    void shouldCalculateSameCurrencyFeeAtClearing() {
        System.out.println("费用组件：验证100 USD按2.3%加0.30 USD计算并在同币种完成限额求值");
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                2001L,
                FeeMode.STANDARD,
                new BigDecimal("2.3"),
                money("0.30", "USD", 2),
                money("0.50", "USD", 2),
                money("5.00", "USD", 2),
                null);
        FeeCalculationCommand command = new FeeCalculationCommand(
                money("100", "USD", 2), rule, List.of(), TierContext.empty(),
                RoundingMode.HALF_UP);

        FeeCalculationResult result = new FeeComponentCalculator().calculate(command);

        assertThat(result.components())
                .extracting(component -> component.componentType())
                .containsExactly(FeeComponentType.PERCENTAGE, FeeComponentType.FIXED);
        assertThat(result.components())
                .extracting(component -> component.amount().amount())
                .containsExactly(new BigDecimal("2.30"), new BigDecimal("0.30"));
        assertThat(result.finalFee().amount()).isEqualByComparingTo("2.60");
        assertThat(result.finalFee().currency()).isEqualTo("USD");
        System.out.println("费用组件结果：百分比2.30 USD，固定费0.30 USD，最终费用2.60 USD");
    }

    /** 跨币种组件和限额必须保留原值并等待结算批次统一使用汇率。 */
    @Test
    void shouldDeferCrossCurrencyFeeEvaluationUntilSettlement() {
        System.out.println("跨币种费用：验证100 EUR配置0.30 USD固定费及USD上下限时不在清分阶段换汇");
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                2002L,
                FeeMode.STANDARD,
                new BigDecimal("2.3"),
                money("0.30", "USD", 2),
                money("0.50", "USD", 2),
                money("5.00", "USD", 2),
                null);
        FeeCalculationCommand command = new FeeCalculationCommand(
                money("100", "EUR", 2), rule, List.of(), TierContext.empty(),
                RoundingMode.HALF_UP);

        FeeCalculationResult result = new FeeComponentCalculator().calculate(command);

        assertThat(result.components())
                .extracting(component -> component.amount().currency() + ":" + component.amount().amount())
                .containsExactly("EUR:2.30", "USD:0.30");
        assertThat(result.feeEvaluationStatus()).isEqualTo(FeeEvaluationStatus.PENDING_SETTLEMENT_RATE);
        assertThat(result.limitEvaluationStatus()).isEqualTo(LimitEvaluationStatus.PENDING_SETTLEMENT_RATE);
        assertThat(result.finalFee()).isNull();
        assertThat(result.minimumFeeUsd().amount()).isEqualByComparingTo("0.50");
        assertThat(result.maximumFeeUsd().amount()).isEqualByComparingTo("5.00");
        System.out.println("跨币种费用结果：保留2.30 EUR与0.30 USD，限额等待结算批次求值");
    }

    /** 标签币种百分比舍入为零时，正数 USD 最低费已可直接确定，不得伪造汇率依赖。 */
    @Test
    void shouldApplyUsdMinimumWhenLabelPercentageRoundsToZero() {
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                2005L,
                FeeMode.STANDARD,
                new BigDecimal("0.1"),
                null,
                money("0.50", "USD", 2),
                money("5.00", "USD", 2),
                null);
        FeeCalculationCommand command = new FeeCalculationCommand(
                money("0.01", "EUR", 2), rule, List.of(), TierContext.empty(),
                RoundingMode.HALF_UP);

        FeeCalculationResult result = new FeeComponentCalculator().calculate(command);

        assertThat(result.feeEvaluationStatus()).isEqualTo(FeeEvaluationStatus.FINAL_AT_CLEARING);
        assertThat(result.limitEvaluationStatus()).isEqualTo(LimitEvaluationStatus.FINAL_AT_CLEARING);
        assertThat(result.appliedLimit()).isEqualTo(AppliedLimit.MINIMUM);
        assertThat(result.finalFee()).isEqualTo(money("0.50", "USD", 2));
        assertThat(result.components()).singleElement().satisfies(component -> {
            assertThat(component.componentType()).isEqualTo(FeeComponentType.LIMIT_ADJUSTMENT);
            assertThat(component.amount()).isEqualTo(money("0.50", "USD", 2));
            assertThat(component.direction()).isEqualTo(EntryDirection.DEBIT);
        });
    }

    /** 最高费降低应收费用时应追加贷方调整组件，不能覆盖原百分比和固定费事实。 */
    @Test
    void shouldAppendCreditAdjustmentWhenMaximumFeeApplies() {
        System.out.println("同币种限额：验证原始费用6.06 USD触发5.00 USD最高费时保留原组件并追加贷方调整");
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                2003L,
                FeeMode.STANDARD,
                new BigDecimal("2.3"),
                money("1.00", "USD", 2),
                money("1.50", "USD", 2),
                money("5.00", "USD", 2),
                null);
        FeeCalculationCommand command = new FeeCalculationCommand(
                money("220", "USD", 2), rule, List.of(), TierContext.empty(),
                RoundingMode.HALF_UP);

        FeeCalculationResult result = new FeeComponentCalculator().calculate(command);

        assertThat(result.appliedLimit()).isEqualTo(AppliedLimit.MAXIMUM);
        assertThat(result.finalFee().amount()).isEqualByComparingTo("5.00");
        assertThat(result.components().get(2).componentType()).isEqualTo(FeeComponentType.LIMIT_ADJUSTMENT);
        assertThat(result.components().get(2).direction()).isEqualTo(EntryDirection.CREDIT);
        assertThat(result.components().get(2).amount().amount()).isEqualByComparingTo("1.06");
        System.out.println("同币种限额结果：原组件不变，追加CREDIT 1.06 USD，最终费用5.00 USD");
    }

    /** 百分比组件必须按币种 exponent 单独舍入，不能默认所有币种都是两位小数。 */
    @ParameterizedTest(name = "{0} exponent={1} expected={2}")
    @CsvSource({
            "JPY, 0, 3",
            "USD, 2, 2.56",
            "KWD, 3, 2.555"
    })
    void shouldRoundPercentageComponentByCurrencyExponent(String currency, int exponent, String expected) {
        System.out.printf("币种精度：验证%s按exponent=%d舍入百分比组件%n", currency, exponent);
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                2004L, FeeMode.STANDARD, new BigDecimal("2.555"),
                null, null, null, null);
        FeeCalculationCommand command = new FeeCalculationCommand(
                money("100", currency, exponent), rule, List.of(), TierContext.empty(),
                RoundingMode.HALF_UP);

        FeeCalculationResult result = new FeeComponentCalculator().calculate(command);

        assertThat(result.components().get(0).amount().amount()).isEqualByComparingTo(expected);
        assertThat(result.components().get(0).amount().amount().scale()).isEqualTo(exponent);
    }

    /** COUNT + VOLUME 应将当前交易计入累计后选择整笔适用档位。 */
    @Test
    void shouldSelectVolumeTierUsingCountAfterCurrentTransaction() {
        System.out.println("阶梯费用：验证countBefore=99时当前第100笔进入第二档并对整笔应用2.0%费率");
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                3001L, FeeMode.TIER, BigDecimal.ZERO,
                null, null, null, TierMetric.COUNT);
        FeeTierSnapshot first = tier(3101L, "0", "100", "2.5", null, null, null);
        FeeTierSnapshot second = tier(3102L, "100", null, "2.0", null, null, null);
        TierContext context = new TierContext(99L, BigDecimal.ZERO, BigDecimal.ZERO);
        FeeCalculationCommand command = new FeeCalculationCommand(
                money("100", "USD", 2), rule, List.of(first, second), context, RoundingMode.HALF_UP);

        FeeCalculationResult result = new FeeComponentCalculator().calculate(command);

        assertThat(result.matchedTierId()).isEqualTo(3102L);
        assertThat(result.matchedTierIds()).containsExactly(3102L);
        assertThat(result.components().get(0).tierId()).isEqualTo(3102L);
        assertThat(result.finalFee().amount()).isEqualByComparingTo("2.00");
        System.out.println("阶梯费用结果：当前第100笔匹配tierId=3102，最终费用2.00 USD");
    }

    /** AMOUNT 阶梯沿用现有 USD 归一累计口径，并按包含本笔后的累计值选择整笔档位。 */
    @Test
    void shouldSelectVolumeTierUsingLabelAmountAfterCurrentTransaction() {
        System.out.println("金额阶梯：验证累计900 USD加本笔200 USD后按第二档1.5%计算整笔");
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                3002L, FeeMode.TIER, BigDecimal.ZERO,
                null, null, null, TierMetric.AMOUNT);
        FeeTierSnapshot first = tier(3201L, "0", "1000", "2.0", null, null, null);
        FeeTierSnapshot second = tier(3202L, "1000", null, "1.5", null, null, null);
        TierContext context = new TierContext(0L, new BigDecimal("900"), new BigDecimal("200"));
        FeeCalculationCommand command = new FeeCalculationCommand(
                money("200", "USD", 2), rule, List.of(first, second), context, RoundingMode.HALF_UP);

        FeeCalculationResult result = new FeeComponentCalculator().calculate(command);

        assertThat(result.matchedTierId()).isEqualTo(3202L);
        assertThat(result.finalFee().amount()).isEqualByComparingTo("3.00");
        assertThat(result.components().get(0).basisAmount().amount()).isEqualByComparingTo("200");
        System.out.println("金额阶梯结果：本笔全部200 USD按第二档计费3.00 USD，不追溯前序交易");
    }

    /** AMOUNT 阶梯必须使用原费用配置的 USD 归一累计事实，百分比仍按标签币种计提。 */
    @Test
    void shouldSelectAmountTierUsingUsdNormalizedFacts() {
        System.out.println("金额阶梯口径：验证EUR标签交易使用USD归一累计选档，百分比仍生成EUR费用组件");
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                3004L, FeeMode.TIER, BigDecimal.ZERO,
                null, null, null, TierMetric.AMOUNT);
        FeeTierSnapshot first = tier(3401L, "0", "1000", "2.0", null, null, null);
        FeeTierSnapshot second = tier(3402L, "1000", null, "1.5", null, null, null);
        TierContext context = new TierContext(
                0L, new BigDecimal("900"), new BigDecimal("220"));
        FeeCalculationCommand command = new FeeCalculationCommand(
                money("200", "EUR", 2), rule, List.of(first, second), context, RoundingMode.HALF_UP);

        FeeCalculationResult result = new FeeComponentCalculator().calculate(command);

        assertThat(result.matchedTierId()).isEqualTo(3402L);
        assertThat(result.components().get(0).basisAmount().currency()).isEqualTo("EUR");
        assertThat(result.components().get(0).amount().amount()).isEqualByComparingTo("3.00");
    }

    /** 冻结快照缺少阶梯下界时必须明确拒绝，不能在排序阶段抛出空指针。 */
    @Test
    void shouldRejectTierSnapshotWithoutLowerBound() {
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                3005L, FeeMode.TIER, BigDecimal.ZERO,
                null, null, null, TierMetric.COUNT);
        FeeTierSnapshot invalidTier = new FeeTierSnapshot(
                3501L, null, null, new BigDecimal("2.0"),
                null, null, null);
        FeeCalculationCommand command = new FeeCalculationCommand(
                money("100", "USD", 2), rule, List.of(invalidTier),
                TierContext.empty(), RoundingMode.HALF_UP);

        assertThatThrownBy(() -> new FeeComponentCalculator().calculate(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative lower bound");
    }

    /** 冻结快照中的阶梯区间必须连续且不能重叠。 */
    @Test
    void shouldRejectOverlappingTierSnapshot() {
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                3006L, FeeMode.TIER, BigDecimal.ZERO,
                null, null, null, TierMetric.AMOUNT);
        FeeTierSnapshot first = tier(3601L, "0", "200", "2.0", null, null, null);
        FeeTierSnapshot second = tier(3602L, "100", null, "1.5", null, null, null);
        FeeCalculationCommand command = new FeeCalculationCommand(
                money("150", "USD", 2), rule, List.of(first, second),
                new TierContext(0L, BigDecimal.ZERO, new BigDecimal("150")),
                RoundingMode.HALF_UP);

        assertThatThrownBy(() -> new FeeComponentCalculator().calculate(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("continuous and non-overlapping");
    }

    /** 冻结快照中的阶梯区间不能存在无法命中的缺口。 */
    @Test
    void shouldRejectTierSnapshotWithGap() {
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                3010L, FeeMode.TIER, BigDecimal.ZERO,
                null, null, null, TierMetric.AMOUNT);
        FeeTierSnapshot first = tier(4001L, "0", "100", "2.0", null, null, null);
        FeeTierSnapshot second = tier(4002L, "200", null, "1.5", null, null, null);
        FeeCalculationCommand command = new FeeCalculationCommand(
                money("150", "USD", 2), rule, List.of(first, second),
                new TierContext(0L, BigDecimal.ZERO, new BigDecimal("150")),
                RoundingMode.HALF_UP);

        assertThatThrownBy(() -> new FeeComponentCalculator().calculate(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("continuous and non-overlapping");
    }

    /** 非末档上界必须严格大于本档下界。 */
    @Test
    void shouldRejectTierSnapshotWithInvalidUpperBound() {
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                3011L, FeeMode.TIER, BigDecimal.ZERO,
                null, null, null, TierMetric.COUNT);
        FeeTierSnapshot first = tier(4101L, "0", "0", "2.0", null, null, null);
        FeeTierSnapshot second = tier(4102L, "0", null, "1.5", null, null, null);
        FeeCalculationCommand command = new FeeCalculationCommand(
                money("100", "USD", 2), rule, List.of(first, second),
                TierContext.empty(), RoundingMode.HALF_UP);

        assertThatThrownBy(() -> new FeeComponentCalculator().calculate(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("upper bound must exceed lower bound");
    }

    /** 冻结快照首档必须从零开始，避免低值交易没有可匹配档位。 */
    @Test
    void shouldRejectTierSnapshotThatDoesNotStartAtZero() {
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                3007L, FeeMode.TIER, BigDecimal.ZERO,
                null, null, null, TierMetric.AMOUNT);
        FeeTierSnapshot invalidTier = tier(3701L, "10", null, "2.0", null, null, null);
        FeeCalculationCommand command = new FeeCalculationCommand(
                money("100", "USD", 2), rule, List.of(invalidTier),
                new TierContext(0L, BigDecimal.ZERO, new BigDecimal("100")),
                RoundingMode.HALF_UP);

        assertThatThrownBy(() -> new FeeComponentCalculator().calculate(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start at zero");
    }

    /** 冻结快照末档必须开放上界，确保未来累计值始终可以匹配。 */
    @Test
    void shouldRejectTierSnapshotWithClosedLastInterval() {
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                3008L, FeeMode.TIER, BigDecimal.ZERO,
                null, null, null, TierMetric.AMOUNT);
        FeeTierSnapshot invalidTier = tier(3801L, "0", "1000", "2.0", null, null, null);
        FeeCalculationCommand command = new FeeCalculationCommand(
                money("100", "USD", 2), rule, List.of(invalidTier),
                new TierContext(0L, BigDecimal.ZERO, new BigDecimal("100")),
                RoundingMode.HALF_UP);

        assertThatThrownBy(() -> new FeeComponentCalculator().calculate(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("last tier upper bound must be empty");
    }

    /** COUNT 阶梯边界必须是整数，禁止冻结无法对应实际笔数的半笔区间。 */
    @Test
    void shouldRejectFractionalCountTierBoundary() {
        FeeRuleSnapshot rule = new FeeRuleSnapshot(
                3009L, FeeMode.TIER, BigDecimal.ZERO,
                null, null, null, TierMetric.COUNT);
        FeeTierSnapshot first = tier(3901L, "0", "100.5", "2.0", null, null, null);
        FeeTierSnapshot second = tier(3902L, "100.5", null, "1.5", null, null, null);
        FeeCalculationCommand command = new FeeCalculationCommand(
                money("100", "USD", 2), rule, List.of(first, second),
                TierContext.empty(), RoundingMode.HALF_UP);

        assertThatThrownBy(() -> new FeeComponentCalculator().calculate(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count tier boundaries must be integers");
    }

    private static FeeTierSnapshot tier(Long id,
                                        String lower,
                                        String upper,
                                        String percentage,
                                        Money fixed,
                                        Money minimum,
                                        Money maximum) {
        return new FeeTierSnapshot(id, new BigDecimal(lower),
                upper == null ? null : new BigDecimal(upper), new BigDecimal(percentage),
                fixed, minimum, maximum);
    }

    private static Money money(String amount, String currency, int exponent) {
        return new Money(new BigDecimal(amount), currency, exponent);
    }
}
