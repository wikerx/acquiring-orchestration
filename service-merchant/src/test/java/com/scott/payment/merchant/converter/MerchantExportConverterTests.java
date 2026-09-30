package com.scott.payment.merchant.converter;

import com.scott.payment.merchant.dto.MerchantFinanceDTOs.FundLedgerResponse;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantExportConverterTests {

    @Test
    void ledgerExportPreservesAmountsAndVisibleFields() {
        LocalDateTime postedTime = LocalDateTime.of(2026, 9, 30, 11, 30);
        FundLedgerResponse source = new FundLedgerResponse();
        source.setLedgerNo("L001");
        source.setBusinessType("REFUND");
        source.setSummary("Refund settled");
        source.setBusinessNo("R001");
        source.setBalanceType("AVAILABLE");
        source.setDirection("DEBIT");
        source.setAmount(new BigDecimal("12.3400"));
        source.setCurrency("USD");
        source.setBalanceBefore(new BigDecimal("50.0000"));
        source.setBalanceAfter(new BigDecimal("37.6600"));
        source.setOperatorName("operator");
        source.setReviewerName("reviewer");
        source.setPostedTime(postedTime);
        source.setTransactionId("internal-transaction");

        var row = MerchantExportConverter.INSTANCE.toLedgerExportRow(source);
        assertThat(row).extracting("ledgerNo", "businessType", "summary", "businessNo",
                        "balanceType", "direction", "currency", "operatorName", "reviewerName", "postedTime")
                .containsExactly("L001", "REFUND", "Refund settled", "R001", "AVAILABLE",
                        "DEBIT", "USD", "operator", "reviewer", postedTime);
        assertThat(row.getAmount()).isEqualTo(new BigDecimal("12.3400"));
        assertThat(row.getBalanceBefore()).isEqualTo(new BigDecimal("50.0000"));
        assertThat(row.getBalanceAfter()).isEqualTo(new BigDecimal("37.6600"));
    }

    @Test
    void refundExportPreservesTransactionAmountAndMerchantMessage() {
        LocalDateTime transactionTime = LocalDateTime.of(2026, 9, 30, 9, 0);
        LocalDateTime completeTime = transactionTime.plusMinutes(5);
        RefundRecord source = new RefundRecord();
        source.setRefundTransactionId("R001");
        source.setSourceTransactionId("P001");
        source.setMerchantOrderNo("ORDER001");
        source.setTransactionType("REFUND");
        source.setRefundScope("PARTIAL");
        source.setTransactionAmount(new BigDecimal("1.2300"));
        source.setTransactionCurrency("EUR");
        source.setTransactionStatus("SUCCESS");
        source.setApprovalStatus("APPROVED");
        source.setMerchantVisibleMessage("Completed");
        source.setPaymentMethod("CARD");
        source.setTransactionDateTime(transactionTime);
        source.setCompleteTime(completeTime);
        source.setRequestReason("internal reason");

        var row = MerchantExportConverter.INSTANCE.toRefundExportRow(source);
        assertThat(row).extracting("refundTransactionId", "sourceTransactionId", "merchantOrderNo",
                        "transactionType", "refundScope", "transactionCurrency", "transactionStatus",
                        "approvalStatus", "merchantVisibleMessage", "paymentMethod", "transactionDateTime", "completeTime")
                .containsExactly("R001", "P001", "ORDER001", "REFUND", "PARTIAL", "EUR",
                        "SUCCESS", "APPROVED", "Completed", "CARD", transactionTime, completeTime);
        assertThat(row.getTransactionAmount()).isEqualTo(new BigDecimal("1.2300"));
    }
}
