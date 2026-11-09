package com.scott.payment.clearing.service;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 未结算历史标签本金修复边界；只追加新修订，不重算费用、保证金或汇率。
 */
public interface ClearingLabelPrincipalRepairService {

    enum ReviewDecision {
        APPROVE,
        REJECT
    }

    /**
     * 冻结当前可修复事实并创建待复核申请，不改变资金事实。
     */
    RepairResult submit(SubmitCommand command);

    /**
     * 由不同操作人复核申请；批准后原子追加新修订并替换候选。
     */
    RepairResult review(ReviewCommand command);

    /**
     * 修复申请命令；金额和币种不由调用方提交，避免浏览器篡改修复目标。
     */
    record SubmitCommand(String requestKey,
                         String transactionId,
                         LocalDateTime transactionDateTime,
                         int expectedFinanceStateVersion,
                         int expectedClearingRevision,
                         long expectedCandidateVersion,
                         String reason,
                         String submitOperator,
                         Instant requestedInstant) {
    }

    /**
     * 修复复核命令；expectedRequestVersion 防止旧页面覆盖终态决定。
     */
    record ReviewCommand(String repairNo,
                         long expectedRequestVersion,
                         ReviewDecision decision,
                         String reviewComment,
                         String reviewOperator,
                         Instant reviewInstant) {
    }

    /**
     * 修复状态与新事实引用，不返回费用、保证金或渠道金额。
     */
    record RepairResult(String repairNo,
                        String status,
                        String transactionId,
                        LocalDateTime transactionDateTime,
                        int oldClearingRevision,
                        Integer newClearingRevision,
                        String newCandidateNo,
                        long version) {
    }
}
