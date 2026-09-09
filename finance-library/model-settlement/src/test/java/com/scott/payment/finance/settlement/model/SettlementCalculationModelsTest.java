package com.scott.payment.finance.settlement.model;

import com.scott.payment.finance.money.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import static com.scott.payment.finance.settlement.model.SettlementCalculationModels.AmountDirection.CREDIT;
import static com.scott.payment.finance.settlement.model.SettlementCalculationModels.FeeComponentKind.PERCENTAGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementCalculationModelsTest
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 验证结算计算模型对必填金额和可选 USD 限额采用显式且稳定的空值边界。
 * @status : create
 */
class SettlementCalculationModelsTest {

    @Test
    void feeGroupShouldAllowMissingOptionalUsdLimits() {
        var command = new SettlementCalculationModels.FeeGroupCommand(
                "FG-1",
                List.of(new SettlementCalculationModels.FeeComponentInput(
                        "PERCENTAGE-1", PERCENTAGE, new Money(new BigDecimal("1.00"), "EUR", 2))),
                null,
                null,
                "USD",
                2,
                RoundingMode.HALF_UP);

        assertThat(command.minimumFeeUsd()).isNull();
        assertThat(command.maximumFeeUsd()).isNull();
    }

    @Test
    void amountLineShouldRejectMissingRequiredAmount() {
        assertThatThrownBy(() -> new SettlementCalculationModels.AmountLine("LINE-1", null, CREDIT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("source amount is required");
    }
}
