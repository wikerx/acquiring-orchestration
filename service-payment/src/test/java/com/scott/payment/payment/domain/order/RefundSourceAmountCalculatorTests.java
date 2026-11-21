package com.scott.payment.payment.domain.order;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.payment.entity.TransactionOperationDO;
import com.scott.payment.payment.entity.TransactionOrderDO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RefundSourceAmountCalculatorTests {
    @Test
    void sourceSuccessAndAllReservationsMustShareOneLimitAndOtherCapturesStayIndependent() {
        TransactionOrderDO root = root();
        TransactionOperationDO source = action("C1", null, "CAPTURE", "SUCCESS", "1000");
        TransactionOperationDO success = action("R1", "C1", "REFUND", "SUCCESS", "200");
        TransactionOperationDO pending = action("R2", "C1", "REFUND", "PENDING", "300");
        TransactionOperationDO other = action("R3", "C2", "REFUND", "PROCESSING", "900");
        var facts = List.of(source, success, pending, other);
        var budget = RefundSourceAmountCalculator.sourceBudget(root, source, facts);
        var reservations = RefundSourceAmountCalculator.pendingForSource(source, List.of(pending, other));
        assertThat(reservations).containsExactly(pending);
        assertThat(FrozenRateFollowUpAmountCalculator.calculateRefund(budget, reservations,
                new BigDecimal("500"), "USD", 2, 2).transactionAmount()).isEqualByComparingTo("500");
        assertThatThrownBy(() -> FrozenRateFollowUpAmountCalculator.calculateRefund(budget, reservations,
                new BigDecimal("500.01"), "USD", 2, 2)).isInstanceOf(ServiceException.class);
        assertThat(root.getAvailableRefundAmount()).isEqualByComparingTo("3000");
    }

    @Test
    void successConvertsReservationWithoutRestoringCapacityAndDefinitiveFailureReleasesIt() {
        TransactionOrderDO root = root();
        TransactionOperationDO source = action("P1", null, "PRE_AUTH_COMPLETION", "SUCCESS", "1000");
        TransactionOperationDO refund = action("R1", "P1", "REFUND", "SUCCESS", "1000");
        var budget = RefundSourceAmountCalculator.sourceBudget(root, source, List.of(source, refund));
        assertThat(budget.getAvailableRefundAmount()).isZero();
        refund.setTransactionStatus("FAILED");
        assertThat(RefundSourceAmountCalculator.sourceBudget(root, source, List.of(source, refund))
                .getAvailableRefundAmount()).isEqualByComparingTo("1000");
    }

    @Test
    void finalSourceRefundUsesItsOwnFrozenCurrencyRemainder() {
        TransactionOrderDO root = root();
        root.setLabelCurrency("AUD"); root.setTransactionRate(new BigDecimal("0.70000521"));
        root.setLabelAvailableRefundAmount(new BigDecimal("4.04")); root.setAvailableRefundAmount(new BigDecimal("2.82"));
        TransactionOperationDO source = action("C1", null, "CAPTURE", "SUCCESS", "89.59");
        source.setTransactionAmount(new BigDecimal("62.71"));
        TransactionOperationDO previous = action("R1", "C1", "REFUND", "SUCCESS", "85.55");
        previous.setTransactionAmount(new BigDecimal("59.89"));
        var budget = RefundSourceAmountCalculator.sourceBudget(root, source, List.of(source, previous));
        var plan = FrozenRateFollowUpAmountCalculator.calculateRefund(budget, List.of(), new BigDecimal("4.04"), "AUD", 2, 2);
        assertThat(plan.transactionAmount()).isEqualByComparingTo("2.82");
        RefundSourceAmountCalculator.validateRootCapacity(root, List.of(), plan);
    }

    @Test
    void legacyRootReservationsAndExplicitCaptureReservationsCannotExceedRootTogether() {
        TransactionOrderDO root = root();
        root.setAvailableRefundAmount(new BigDecimal("1000")); root.setLabelAvailableRefundAmount(new BigDecimal("1000"));
        var source = action("C1", null, "CAPTURE", "SUCCESS", "1000");
        var legacy = action("R1", "ROOT", "REFUND", "PENDING", "800");
        var budget = RefundSourceAmountCalculator.sourceBudget(root, source, List.of(source, legacy));
        var plan = FrozenRateFollowUpAmountCalculator.calculateRefund(budget, List.of(), new BigDecimal("300"), "USD", 2, 2);
        assertThatThrownBy(() -> RefundSourceAmountCalculator.validateRootCapacity(root, List.of(legacy), plan))
                .isInstanceOf(ServiceException.class);
    }

    private TransactionOrderDO root() {
        TransactionOrderDO root = new TransactionOrderDO();
        root.setOperationId("ROOT");
        root.setLabelCurrency("USD");
        root.setTransactionCurrency("USD");
        root.setTransactionRate(BigDecimal.ONE);
        root.setLabelAmount(new BigDecimal("3000"));
        root.setTransactionAmount(new BigDecimal("3000"));
        root.setAvailableRefundAmount(new BigDecimal("3000"));
        root.setLabelAvailableRefundAmount(new BigDecimal("3000"));
        return root;
    }

    private TransactionOperationDO action(String id, String source, String type, String status, String amount) {
        TransactionOperationDO operation = new TransactionOperationDO();
        operation.setOperationId("ROOT");
        operation.setTransactionId(id);
        operation.setSourceTransactionId(source);
        operation.setTransactionType(type);
        operation.setTransactionStatus(status);
        operation.setLabelAmount(new BigDecimal(amount));
        operation.setTransactionAmount(new BigDecimal(amount));
        return operation;
    }
}
