package com.scott.payment.admin.application.transaction;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
class ConsoleTransactionPolicyTests {
    @Test void closureAndUnknownActionsHideConflictingEntriesAcrossPages() {
        var pre = action("P1", null, "PRE_AUTHORIZATION", "SUCCESS", "1200");
        pre.setLabelAvailableCaptureAmount(new BigDecimal("1200"));
        pre.setLabelCapturedAmount(BigDecimal.ZERO);
        var completed = action("C1", "P1", "PRE_AUTH_COMPLETION", "SUCCESS", "1000");
        ConsoleTransactionPolicy.enrich(List.of(pre, completed), List.of(pre, completed));
        assertThat(pre.getAvailableActions()).isEmpty();
        assertThat(completed.getAvailableActions()).containsExactly("REFUND");
        completed.setTransactionStatus("FAILED");
        ConsoleTransactionPolicy.enrich(List.of(pre), List.of(pre, completed));
        assertThat(pre.getAvailableActions()).containsExactly("PRE_AUTH_COMPLETION", "VOID");
        completed.setTransactionStatus("PROCESSING");
        ConsoleTransactionPolicy.enrich(List.of(pre), List.of(pre, completed));
        assertThat(pre.getAvailableActions()).isEmpty();
    }
    @Test void eachCaptureHasIndependentSuccessfulAndPendingRefundAmounts() {
        var capture = action("C1", null, "CAPTURE", "SUCCESS", "1000");
        var success = action("R1", "C1", "REFUND", "SUCCESS", "200");
        var pending = action("R2", "C1", "REFUND", "PENDING", "300");
        var other = action("R3", "C2", "REFUND", "PROCESSING", "900");
        var facts = List.of(capture, success, pending, other);
        var context = ConsoleTransactionPolicy.refundContext(capture, facts);
        assertThat(context.refundedAmount()).isEqualByComparingTo("200");
        assertThat(context.pendingRefundAmount()).isEqualByComparingTo("300");
        assertThat(context.availableRefundAmount()).isEqualByComparingTo("500");
        pending.setLabelAmount(new BigDecimal("800"));
        ConsoleTransactionPolicy.enrich(List.of(capture), facts);
        assertThat(capture.getAvailableActions()).isEmpty();
        pending.setTransactionStatus("FAILED");
        ConsoleTransactionPolicy.enrich(List.of(capture), facts);
        assertThat(capture.getAvailableActions()).containsExactly("REFUND");
        assertThat(capture.getRefundContext().availableRefundAmount()).isEqualByComparingTo("800");
    }
    @Test void rawAuthorizationIncrementVoidAndRefundNeverExposeRefundEntries() {
        for (String type : List.of("AUTHORIZATION", "PRE_AUTHORIZATION", "INCREMENTAL_AUTHORIZATION", "VOID", "REFUND")) {
            var row = action("T1", null, type, "SUCCESS", "1000");
            ConsoleTransactionPolicy.enrich(List.of(row), List.of(row));
            assertThat(row.getAvailableActions()).doesNotContain("REFUND", "INCREMENTAL_AUTHORIZATION");
        }
    }
    @Test void captured28Point46NeverInherits428Point46AuthorizationRefundProjection() {
        var authorization = action("AUTH", null, "AUTHORIZATION", "SUCCESS", "428.46");
        var capture = action("CAPTURE_SMALL", "AUTH", "CAPTURE", "SUCCESS", "28.46");
        var sibling = action("CAPTURE_OTHER", "AUTH", "CAPTURE", "SUCCESS", "400.00");
        for (var row : List.of(authorization, capture, sibling)) {
            row.setLabelCurrency("CNY");
            row.setTransactionCurrency("CNY");
            row.setAvailableRefundAmount(new BigDecimal("428.46"));
            row.setLabelAvailableRefundAmount(new BigDecimal("428.46"));
        }
        var refund = action("REFUND_SMALL", "CAPTURE_SMALL", "REFUND", "PENDING", "8.46");
        refund.setLabelCurrency("CNY");
        refund.setTransactionCurrency("CNY");
        var facts = List.of(authorization, capture, sibling, refund);
        ConsoleTransactionPolicy.enrich(List.of(capture, sibling), facts);
        assertThat(capture.getRefundContext().availableRefundAmount()).isEqualByComparingTo("20.00");
        assertThat(sibling.getRefundContext().availableRefundAmount()).isEqualByComparingTo("400.00");
        refund.setTransactionStatus("FAILED");
        ConsoleTransactionPolicy.enrich(List.of(capture), facts);
        assertThat(capture.getRefundContext().availableRefundAmount()).isEqualByComparingTo("28.46");
        assertThat(capture.getRefundContext().pendingRefundAmount()).isZero();
    }
    private TransactionOperationResponse action(String id, String source, String type, String status, String amount) {
        var row = new TransactionOperationResponse();
        row.setTransactionId(id); row.setOperationId("ROOT"); row.setSourceTransactionId(source);
        row.setTransactionType(type); row.setTransactionStatus(status);
        row.setLabelAmount(new BigDecimal(amount)); row.setLabelCurrency("USD"); row.setTransactionCurrency("USD");
        row.setCurrencyExponent(2);
        return row;
    }
}
