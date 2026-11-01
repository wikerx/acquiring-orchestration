package com.scott.payment.clearing.service;

import java.time.Instant;

/**
 * 阶梯期间重放用例边界；负责幂等申请、双人复核和自动推进，不处理汇率、保证金覆盖或余额入账。
 */
public interface TierPeriodReplayService {

    /**
     * 阶梯期间重放的双人复核决定。
     */
    enum ReviewDecision {
        /**
         * 批准重放申请并允许创建有序重放项。
         */
        APPROVE,
        /**
         * 拒绝重放申请并终止自动执行。
         */
        REJECT
    }

    /**
     * 提交一个商户、不可变费用版本和月份范围的重放申请。
     */
    ReplayResult submit(SubmitCommand command);

    /**
     * 复核重放申请；批准时冻结完整期间并自动进入有序重放。
     */
    ReplayResult review(ReviewCommand command);

    /**
     * 扫描并推进当前可运行重放，每个动作使用独立短事务。
     */
    int runDue(int limit, Instant now);

    /**
     * 阶梯期间重放申请命令，携带商户、费用版本和目标期间的不可变身份。
     */
    record SubmitCommand(String requestKey,
                         String merchantId,
                         long feePlanId,
                         long feePlanVersionId,
                         long triggerFeeRuleId,
                         String periodKey,
                         String reason,
                         String submitOperator,
                         Instant requestedInstant) {
    }

    /**
     * 阶梯期间重放复核命令，携带预期版本和复核人快照。
     */
    record ReviewCommand(String replayNo,
                         long expectedRequestVersion,
                         ReviewDecision decision,
                         String reviewComment,
                         String reviewOperator,
                         Instant reviewInstant) {
    }

    /**
     * 阶梯期间重放申请或执行后的可观察结果。
     */
    record ReplayResult(String replayNo,
                        String status,
                        int itemCount,
                        int completedCount,
                        long version) {
    }
}
