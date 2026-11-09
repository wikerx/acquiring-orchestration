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
 * 验证请款、退款、撤销和增量授权始终使用首笔交易冻结汇率，并同时保护标签与渠道两套额度。
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

    /**
     * 跨币种部分退款应使用原交易冻结汇率计算渠道金额。
     */
    @Test
    void shouldConvertPartialRefundWithFrozenRate() {
        TransactionOrderDO sourceOrderDO = refundableOrder("100.00", "110.00", "1.10000000");

        FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan =
                FrozenRateFollowUpAmountCalculator.calculateRefund(
                        sourceOrderDO, List.of(), new BigDecimal("20.00"), "EUR", 2, 2);

        assertThat(amountPlan.labelAmount()).isEqualByComparingTo("20.00");
        assertThat(amountPlan.labelCurrency()).isEqualTo("EUR");
        assertThat(amountPlan.transactionAmount()).isEqualByComparingTo("22.00");
        assertThat(amountPlan.transactionCurrency()).isEqualTo("USD");
    }

    /**
     * 双端控制台提交标签币种退款后，支付核心必须沿用原交易冻结汇率生成渠道金额。
     */
    @Test
    void shouldConvertAudPortalRefundToUsdWithSourceFrozenRate() {
        TransactionOrderDO sourceOrderDO = sourceOrder();
        sourceOrderDO.setLabelAmount(new BigDecimal("59.36"));
        sourceOrderDO.setLabelAvailableRefundAmount(new BigDecimal("59.36"));
        sourceOrderDO.setTransactionAmount(new BigDecimal("42.28"));
        sourceOrderDO.setAvailableRefundAmount(new BigDecimal("42.28"));
        sourceOrderDO.setTransactionRate(new BigDecimal("0.71233544"));

        FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan =
                FrozenRateFollowUpAmountCalculator.calculateRefund(
                        sourceOrderDO, List.of(), new BigDecimal("10.00"), "AUD", 2, 2);

        assertThat(amountPlan.labelAmount()).isEqualByComparingTo("10.00");
        assertThat(amountPlan.labelCurrency()).isEqualTo("AUD");
        assertThat(amountPlan.transactionAmount()).isEqualByComparingTo("7.12");
        assertThat(amountPlan.transactionCurrency()).isEqualTo("USD");
        assertThat(amountPlan.transactionRate()).isEqualByComparingTo("0.71233544");
    }

    /**
     * 尾笔全部剩余退款直接使用渠道剩余额度，避免多次四舍五入留下尾差。
     */
    @Test
    void shouldUseExactTransactionRemainderForFinalRefund() {
        TransactionOrderDO sourceOrderDO = refundableOrder("1.00", "0.34", "0.33333333");

        FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan =
                FrozenRateFollowUpAmountCalculator.calculateRefund(
                        sourceOrderDO, List.of(), new BigDecimal("1.00"), null, 2, 2);

        assertThat(amountPlan.transactionAmount()).isEqualByComparingTo("0.34");
    }

    /**
     * 处理中退款必须同时预占标签额度和渠道额度。
     */
    @Test
    void shouldReserveBothAmountsForNonTerminalRefund() {
        TransactionOrderDO sourceOrderDO = refundableOrder("100.00", "110.00", "1.10000000");
        TransactionOperationDO pendingRefund = refundOperation("30.00", "33.00");

        assertThatThrownBy(() -> FrozenRateFollowUpAmountCalculator.calculateRefund(
                sourceOrderDO, List.of(pendingRefund), new BigDecimal("80.00"), "EUR", 2, 2))
                .isInstanceOf(ServiceException.class);
    }

    /**
     * 即使标签口径仍有余额，换算后的渠道金额超额也必须拒绝。
     */
    @Test
    void shouldRejectRefundWhenTransactionBalanceIsInsufficient() {
        TransactionOrderDO sourceOrderDO = refundableOrder("100.00", "10.00", "0.20000000");

        assertThatThrownBy(() -> FrozenRateFollowUpAmountCalculator.calculateRefund(
                sourceOrderDO, List.of(), new BigDecimal("60.00"), "EUR", 2, 2))
                .isInstanceOf(ServiceException.class);
    }

    /**
     * 跨币种历史交易缺少标签口径事实时不得通过汇率反推后继续退款。
     */
    @Test
    void shouldRejectCrossCurrencyRefundWhenLabelBalanceIsMissing() {
        TransactionOrderDO sourceOrderDO = refundableOrder("100.00", "110.00", "1.10000000");
        sourceOrderDO.setLabelAvailableRefundAmount(null);

        assertThatThrownBy(() -> FrozenRateFollowUpAmountCalculator.calculateRefund(
                sourceOrderDO, List.of(), new BigDecimal("20.00"), "EUR", 2, 2))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("label refund balance is missing");
    }

    /**
     * 跨币种撤销必须分别使用标签和渠道两套剩余授权金额，并沿用冻结汇率元数据。
     */
    @Test
    void shouldUseBothRemainingAuthorizationAmountsForVoid() {
        TransactionOrderDO sourceOrderDO = voidableOrder();

        FrozenRateFollowUpAmountCalculator.AmountPlan amountPlan =
                FrozenRateFollowUpAmountCalculator.calculateVoid(sourceOrderDO, null, null);

        assertThat(amountPlan.labelAmount()).isEqualByComparingTo("80.00");
        assertThat(amountPlan.labelCurrency()).isEqualTo("EUR");
        assertThat(amountPlan.transactionAmount()).isEqualByComparingTo("88.00");
        assertThat(amountPlan.transactionCurrency()).isEqualTo("USD");
        assertThat(amountPlan.transactionRate()).isEqualByComparingTo("1.10000000");
        assertThat(amountPlan.rateSource()).isEqualTo("PLATFORM");
        assertThat(amountPlan.edcEnabled()).isEqualTo(1);
    }

    /**
     * 当前撤销语义是释放全部剩余授权，显式传入部分标签金额必须拒绝。
     */
    @Test
    void shouldRejectPartialVoidAmount() {
        assertThatThrownBy(() -> FrozenRateFollowUpAmountCalculator.calculateVoid(
                voidableOrder(), new BigDecimal("20.00"), "EUR"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("full remaining label amount");
    }

    /**
     * 跨币种历史主单缺少标签授权累计事实时不得反推商户撤销金额。
     */
    @Test
    void shouldRejectCrossCurrencyVoidWhenLabelAuthorizationBalanceIsMissing() {
        TransactionOrderDO sourceOrderDO = voidableOrder();
        sourceOrderDO.setLabelAuthorizedAmount(null);

        assertThatThrownBy(() -> FrozenRateFollowUpAmountCalculator.calculateVoid(
                sourceOrderDO, null, null))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("label authorization balance is missing");
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

    /**
     * 构建可退款的跨币种主单。
     */
    private TransactionOrderDO refundableOrder(String labelAvailable,
                                               String transactionAvailable,
                                               String transactionRate) {
        TransactionOrderDO sourceOrderDO = new TransactionOrderDO();
        sourceOrderDO.setLabelCurrency("EUR");
        sourceOrderDO.setLabelAmount(new BigDecimal("100.00"));
        sourceOrderDO.setLabelAvailableRefundAmount(new BigDecimal(labelAvailable));
        sourceOrderDO.setTransactionCurrency("USD");
        sourceOrderDO.setTransactionAmount(new BigDecimal("110.00"));
        sourceOrderDO.setAvailableRefundAmount(new BigDecimal(transactionAvailable));
        sourceOrderDO.setTransactionRate(new BigDecimal(transactionRate));
        sourceOrderDO.setRateSource("PLATFORM");
        sourceOrderDO.setRateTime(LocalDateTime.of(2026, 10, 31, 10, 30));
        sourceOrderDO.setEdcEnabled(1);
        return sourceOrderDO;
    }

    /**
     * 构建一笔已经部分释放授权、仍可全额撤销剩余额度的跨币种主单。
     */
    private TransactionOrderDO voidableOrder() {
        TransactionOrderDO sourceOrderDO = new TransactionOrderDO();
        sourceOrderDO.setLabelCurrency("EUR");
        sourceOrderDO.setLabelAmount(new BigDecimal("100.00"));
        sourceOrderDO.setLabelAuthorizedAmount(new BigDecimal("100.00"));
        sourceOrderDO.setLabelAuthorizedCancelAmount(new BigDecimal("20.00"));
        sourceOrderDO.setTransactionCurrency("USD");
        sourceOrderDO.setTransactionAmount(new BigDecimal("110.00"));
        sourceOrderDO.setAuthorizedAmount(new BigDecimal("110.00"));
        sourceOrderDO.setAuthorizedCancelAmount(new BigDecimal("22.00"));
        sourceOrderDO.setTransactionRate(new BigDecimal("1.10000000"));
        sourceOrderDO.setRateSource("PLATFORM");
        sourceOrderDO.setRateTime(LocalDateTime.of(2026, 10, 31, 10, 30));
        sourceOrderDO.setDccEnabled(0);
        sourceOrderDO.setEdcEnabled(1);
        return sourceOrderDO;
    }

    /**
     * 构建一笔已占用退款额度的非终态动作。
     */
    private TransactionOperationDO refundOperation(String labelAmount, String transactionAmount) {
        TransactionOperationDO operationDO = new TransactionOperationDO();
        operationDO.setTransactionType(PaymentTransactionTypeEnum.REFUND.getCode());
        operationDO.setTransactionStatus(PaymentTransactionStatusEnum.PROCESSING.getCode());
        operationDO.setLabelAmount(new BigDecimal(labelAmount));
        operationDO.setLabelCurrency("EUR");
        operationDO.setTransactionAmount(new BigDecimal(transactionAmount));
        operationDO.setTransactionCurrency("USD");
        return operationDO;
    }
}
