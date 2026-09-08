package com.scott.payment.settlement.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/** 内部手动交易或保证金结算预览、后台生成和进度查询契约。 */
public final class SettlementManualReviewModels {

    private SettlementManualReviewModels() {
    }

    public record PreviewCommand(String reviewType,
                                 String requestKey,
                                 String merchantId,
                                 Long settlementProfileId,
                                 String paymentType,
                                 String paymentMethod,
                                 String reason,
                                 SettlementOperatorSnapshot operator) {
        public PreviewCommand {
            if (!List.of("REGULAR", "RESERVE_RELEASE").contains(reviewType)) {
                throw new IllegalArgumentException("manual settlement review type is invalid");
            }
            requireText(requestKey, "request key");
            requireText(merchantId, "merchant id");
            Objects.requireNonNull(settlementProfileId, "settlement profile id is required");
            requireText(reason, "settlement reason");
            Objects.requireNonNull(operator, "settlement operator is required");
        }
    }

    public record StartCommand(String requestKey, long expectedVersion) {
        public StartCommand {
            requireText(requestKey, "request key");
            if (expectedVersion < 0) throw new IllegalArgumentException("expected version is invalid");
        }
    }

    public record PreviewLine(String sourceCurrency,
                              int sourceCurrencyExponent,
                              long transactionCount,
                              BigDecimal grossAmount,
                              BigDecimal platformFeeAmount,
                              BigDecimal reserveAmount,
                              BigDecimal releasedReserveAmount,
                              BigDecimal netSettlementAmount,
                              long pendingFeeCount,
                              String reserveDelayUnit,
                              Integer minimumReserveDelayDays,
                              Integer maximumReserveDelayDays,
                              LocalDate earliestExpectedReleaseDate,
                              LocalDate latestExpectedReleaseDate) {
    }

    public record TaskResult(String taskNo,
                             String reviewOrderNo,
                             String taskStatus,
                             String reviewType,
                             String merchantId,
                             Long settlementProfileId,
                             Long settlementAccountId,
                             String targetCurrency,
                             int targetCurrencyExponent,
                             String paymentType,
                             String paymentMethod,
                             String submitReason,
                             LocalDate businessDate,
                             LocalDateTime cutoffEndTime,
                             long snapshotMaxCandidateId,
                             int expectedCandidateCount,
                             int processedCandidateCount,
                             int lockedCandidateCount,
                             int progressPercent,
                             String initialDelayUnit,
                             int initialDelayDays,
                             int regularDelayDays,
                             String settlementFrequency,
                             Integer frequencyDay,
                             List<PreviewLine> preview,
                             int retryCount,
                             String failureCode,
                             String failureMessage,
                             LocalDateTime startedTime,
                             LocalDateTime completedTime,
                             long version) {
        public TaskResult {
            preview = preview == null ? List.of() : List.copyOf(preview);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }
}
