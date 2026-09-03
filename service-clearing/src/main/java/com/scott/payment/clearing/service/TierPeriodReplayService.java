package com.scott.payment.clearing.service;

import java.time.Instant;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TierPeriodReplayService
 * @date : 2026-08-26 19:30
 * @email : scott_x@163.com
 * @description : 阶梯期间重放用例边界；负责幂等申请、双人复核和自动推进，不处理汇率、保证金覆盖或余额入账。
 * @status : create
 */
public interface TierPeriodReplayService {

    enum ReviewDecision {
        /**
         * APPROVE 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        APPROVE,
        REJECT
    }

    /** 提交一个商户、不可变费用版本和月份范围的重放申请。 */
    ReplayResult submit(SubmitCommand command);

    /** 复核重放申请；批准时冻结完整期间并自动进入有序重放。 */
    ReplayResult review(ReviewCommand command);

    /** 扫描并推进当前可运行重放，每个动作使用独立短事务。 */
    int runDue(int limit, Instant now);

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

    record ReviewCommand(String replayNo,
                         long expectedRequestVersion,
                         ReviewDecision decision,
                         String reviewComment,
                         String reviewOperator,
                         Instant reviewInstant) {
    }

    record ReplayResult(String replayNo,
                        String status,
                        int itemCount,
                        int completedCount,
                        long version) {
    }
}
