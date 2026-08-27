package com.scott.payment.clearing.service;

import com.scott.payment.finance.reserve.model.ReserveCalculationModels.ReserveAdjustmentDirection;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 经双人复核的保证金标签币种差额调整边界；不读取汇率、不写商户余额。 */
public interface ReserveAdjustmentService {

    enum ReviewDecision {
        APPROVE,
        REJECT
    }

    ReserveAdjustmentResult submit(SubmitCommand command);

    ReserveAdjustmentResult review(ReviewCommand command);

    record SubmitCommand(String requestKey,
                         String reserveStateId,
                         String originalTransactionId,
                         LocalDateTime originalTransactionDateTime,
                         long expectedReserveStateVersion,
                         ReserveAdjustmentDirection direction,
                         BigDecimal adjustmentAmount,
                         LocalDate requestedReleaseDate,
                         String reason,
                         String submitOperator,
                         Instant requestedInstant) {
    }

    record ReviewCommand(String adjustmentNo,
                         long expectedRequestVersion,
                         ReviewDecision decision,
                         String reviewComment,
                         String reviewOperator,
                         Instant reviewInstant) {
    }

    record ReserveAdjustmentResult(String adjustmentNo,
                                   String status,
                                   String transactionId,
                                   int sourceRevision,
                                   long version) {
    }
}
