package com.scott.payment.settlement.dto;

import java.time.LocalDateTime;

/** 大批量结算预审的异步决策任务契约。 */
public final class SettlementReviewDecisionModels {

    private SettlementReviewDecisionModels() {
    }

    public record TaskResult(String taskNo,
                             String reviewOrderNo,
                             String decisionAction,
                             String taskStatus,
                             int totalSegmentCount,
                             int processedSegmentCount,
                             int resultBatchCount,
                             int progressPercent,
                             String firstSettlementBatchNo,
                             int retryCount,
                             String failureCode,
                             String failureMessage,
                             LocalDateTime startedTime,
                             LocalDateTime completedTime,
                             long version,
                             boolean recoverable) {
    }
}
