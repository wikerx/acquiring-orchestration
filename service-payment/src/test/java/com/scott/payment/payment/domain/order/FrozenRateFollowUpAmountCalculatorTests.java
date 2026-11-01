package com.scott.payment.payment.domain.order;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.domain.state.PaymentTransactionStatusEnum;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 验证后续授权类动作始终使用首笔交易冻结汇率。
 */
class FrozenRateFollowUpAmountCalculatorTests {

    /**
     * 部分请款应按首笔冻结汇率换算渠道金额。
     */
    @Test
    void shouldConvertPartialCaptureWithFrozenRate() {
        TransactionOrderDO sourceOrderDO = sourceOrder();

        FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan =
                FrozenRateFollowUpAmountCalculator.calculateCapture(
                        sourceOrderDO,
                        List.of(),
                        new BigDecimal("85.55"),
                        "AUD",
                        2,
                        2);

        assertThat(amountPlan.labelCurrency()).isEqualTo("AUD");
        assertThat(amountPlan.labelAmount()).isEqualByComparingTo("85.55");
        assertThat(amountPlan.transactionCurrency()).isEqualTo("USD");
        assertThat(amountPlan.transactionAmount()).isEqualByComparingTo("59.89");
        assertThat(amountPlan.transactionRate()).isEqualByComparingTo("0.70000521");
        assertThat(amountPlan.rateSource()).isEqualTo("PLATFORM");
        assertThat(amountPlan.rateTime()).isEqualTo(LocalDateTime.of(2026, 10, 31, 10, 30));
        assertThat(amountPlan.edcEnabled()).isEqualTo(1);
    }

    /**
     * 最后一笔请款应直接使用主单剩余渠道金额，消除累计四舍五入差额。
     */
    @Test
    void shouldUseExactTransactionRemainderForFinalCapture() {
        TransactionOrderDO sourceOrderDO = sourceOrder();
        sourceOrderDO.setCapturedAmount(new BigDecimal("59.89"));
        sourceOrderDO.setAvailableCaptureAmount(new BigDecimal("2.82"));
        TransactionOperationDO previousCapture = successfulOperation(
                PaymentTransactionTypeEnum.CAPTURE, "85.55", "59.89");

        FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan =
                FrozenRateFollowUpAmountCalculator.calculateCapture(
                        sourceOrderDO,
                        List.of(previousCapture),
                        new BigDecimal("4.04"),
                        null,
                        2,
                        2);

        assertThat(amountPlan.labelCurrency()).isEqualTo("AUD");
        assertThat(amountPlan.labelAmount()).isEqualByComparingTo("4.04");
        assertThat(amountPlan.transactionAmount()).isEqualByComparingTo("2.82");
    }

    /**
     * 增量授权应复用首笔交易的汇率、来源、时间和 EDC 标志。
     */
    @Test
    void shouldReuseFrozenRateMetadataForIncrementalAuthorization() {
        FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan =
                FrozenRateFollowUpAmountCalculator.calculateIncrementalAuthorization(
                        sourceOrder(),
                        new BigDecimal("10.00"),
                        "AUD",
                        2,
                        2);

        assertThat(amountPlan.transactionAmount()).isEqualByComparingTo("7.00");
        assertThat(amountPlan.transactionRate()).isEqualByComparingTo("0.70000521");
        assertThat(amountPlan.rateSource()).isEqualTo("PLATFORM");
        assertThat(amountPlan.rateTime()).isEqualTo(LocalDateTime.of(2026, 10, 31, 10, 30));
        assertThat(amountPlan.edcEnabled()).isEqualTo(1);
    }

    /**
     * 商户显式传入的后续动作币种必须与首笔交易标签币种一致。
     */
    @Test
    void shouldRejectCaptureCurrencyDifferentFromSourceLabelCurrency() {
        assertThatThrownBy(() -> FrozenRateFollowUpAmountCalculator.calculateCapture(
                sourceOrder(),
                List.of(),
                new BigDecimal("85.55"),
                "EUR",
                2,
                2))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("request currency must match source label currency");
    }

    private TransactionOrderDO sourceOrder() {
        TransactionOrderDO sourceOrderDO = new TransactionOrderDO();
        sourceOrderDO.setLabelCurrency("AUD");
        sourceOrderDO.setLabelAmount(new BigDecimal("89.59"));
        sourceOrderDO.setTransactionCurrency("USD");
        sourceOrderDO.setTransactionAmount(new BigDecimal("62.71"));
        sourceOrderDO.setAuthorizedAmount(new BigDecimal("62.71"));
        sourceOrderDO.setCapturedAmount(BigDecimal.ZERO);
        sourceOrderDO.setAvailableCaptureAmount(new BigDecimal("62.71"));
        sourceOrderDO.setTransactionRate(new BigDecimal("0.70000521"));
        sourceOrderDO.setRateSource("PLATFORM");
        sourceOrderDO.setRateTime(LocalDateTime.of(2026, 10, 31, 10, 30));
        sourceOrderDO.setDccEnabled(0);
        sourceOrderDO.setEdcEnabled(1);
        return sourceOrderDO;
    }

    private TransactionOperationDO successfulOperation(PaymentTransactionTypeEnum transactionType,
                                                       String labelAmount,
                                                       String transactionAmount) {
        TransactionOperationDO operationDO = new TransactionOperationDO();
        operationDO.setTransactionType(transactionType.getCode());
        operationDO.setTransactionStatus(PaymentTransactionStatusEnum.SUCCESS.getCode());
        operationDO.setLabelCurrency("AUD");
        operationDO.setLabelAmount(new BigDecimal(labelAmount));
        operationDO.setTransactionCurrency("USD");
        operationDO.setTransactionAmount(new BigDecimal(transactionAmount));
        return operationDO;
    }
}
