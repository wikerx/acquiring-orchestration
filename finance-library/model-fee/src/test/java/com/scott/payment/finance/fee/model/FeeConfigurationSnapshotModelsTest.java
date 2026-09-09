package com.scott.payment.finance.fee.model;

import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeMode;
import com.scott.payment.finance.fee.model.FeeCalculationModels.FeeRuleSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeCurrencyPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeRuleConfigurationSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.FeeVersionSnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.PercentageBasis;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.RefundFeeReturnPolicy;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReserveBasis;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReservePolicySnapshot;
import com.scott.payment.finance.fee.model.FeeConfigurationSnapshotModels.ReserveRefundPolicy;
import com.scott.payment.finance.money.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FeeConfigurationSnapshotModelsTest
 * @date : 2026-08-25 23:40
 * @email : scott_x@163.com
 * @description : 验证交易动作冻结的费用版本快照保留标签币种百分比、USD 固定费/上下限、清分策略和不可变规则集合。
 * @status : create
 */
class FeeConfigurationSnapshotModelsTest {

    @Test
    void shouldKeepConfiguredCurrencyPolicyAndDefensivelyCopyRules() {
        FeeRuleConfigurationSnapshot rule = new FeeRuleConfigurationSnapshot(
                2001L,
                "TRANSACTION_FEE",
                "PAYMENT",
                "BANK_CARD",
                "VISA",
                "NONE",
                "NOT_APPLICABLE",
                new FeeRuleSnapshot(
                        2001L,
                        FeeMode.STANDARD,
                        new BigDecimal("2.3"),
                        new Money(new BigDecimal("0.30"), "USD", 2),
                        new Money(new BigDecimal("0.50"), "USD", 2),
                        new Money(new BigDecimal("5.00"), "USD", 2),
                        null),
                List.of());
        List<FeeRuleConfigurationSnapshot> sourceRules = new ArrayList<>(List.of(rule));

        FeeVersionSnapshot snapshot = new FeeVersionSnapshot(
                3,
                "M00010001",
                1001L,
                1008L,
                8,
                LocalDateTime.of(2026, 8, 25, 10, 0, 0, 123_000_000),
                "USD",
                PercentageBasis.LABEL_AMOUNT,
                FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP,
                new ReservePolicySnapshot(
                        new BigDecimal("10"), ReserveBasis.LABEL_AMOUNT, "D", 180,
                        ReserveRefundPolicy.PROPORTIONAL_RETURN),
                RefundFeeReturnPolicy.NONE,
                sourceRules,
                "a".repeat(64));
        sourceRules.clear();

        assertThat(snapshot.rules()).singleElement().isEqualTo(rule);
        assertThat(snapshot.merchantId()).isEqualTo("M00010001");
        assertThat(snapshot.rules().get(0).calculationRule().fixedFeeUsd().currency()).isEqualTo("USD");
        assertThat(snapshot.rules().get(0).calculationRule().minimumFeeUsd().currency()).isEqualTo("USD");
        assertThat(snapshot.rules().get(0).calculationRule().maximumFeeUsd().currency()).isEqualTo("USD");
        assertThat(snapshot.rules()).isUnmodifiable();
    }

    @Test
    void shouldRejectIncompleteOrUnsupportedClearingPolicy() {
        ReservePolicySnapshot reserve = new ReservePolicySnapshot(
                BigDecimal.ZERO, ReserveBasis.LABEL_AMOUNT, "D", 180,
                ReserveRefundPolicy.PROPORTIONAL_RETURN);

        assertThatIllegalArgumentException().isThrownBy(() -> new FeeVersionSnapshot(
                1, "M00010001", 1L, 2L, 1, LocalDateTime.now(), "USD",
                PercentageBasis.LABEL_AMOUNT, FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP, reserve, RefundFeeReturnPolicy.NONE, List.of(), null));
        assertThatIllegalArgumentException().isThrownBy(() -> new FeeVersionSnapshot(
                3, " ", 1L, 2L, 1, LocalDateTime.now(), "USD",
                PercentageBasis.LABEL_AMOUNT, FeeCurrencyPolicy.LABEL_PERCENTAGE_USD_FIXED_LIMITS,
                RoundingMode.HALF_UP, reserve, RefundFeeReturnPolicy.NONE,
                List.of(new FeeRuleConfigurationSnapshot(
                        1L, "TRANSACTION_FEE", "PAYMENT", "BANK_CARD", "ALL", "NONE",
                        "NOT_APPLICABLE", new FeeRuleSnapshot(
                        1L, FeeMode.STANDARD, BigDecimal.ONE, null, null, null, null), List.of())),
                "a".repeat(64)));
        assertThatNullPointerException().isThrownBy(() -> new FeeRuleConfigurationSnapshot(
                1L, "TRANSACTION_FEE", "PAYMENT", "BANK_CARD", "ALL", "NONE",
                "NOT_APPLICABLE", null, List.of()));
    }
}
