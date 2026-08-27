package com.scott.payment.clearing.dto;

import com.scott.payment.clearing.domain.model.ClearingOperationFacts;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingClaimResult
 * @date : 2026-08-26 09:12
 * @email : scott_x@163.com
 * @description : 阶段A领取结果，向应用编排层返回租约结果和计算所需的非敏感动作事实。
 * @status : create
 * @param outcome 领取结果
 * @param financeStateId 动作财务状态号
 * @param clearingRevision 当前有效清分修订号
 * @param financeStateVersion 领取后的财务状态 CAS 版本
 * @param operation 数据库权威动作事实
 */
public record ClearingClaimResult(Outcome outcome,
                                  String financeStateId,
                                  int clearingRevision,
                                  int financeStateVersion,
                                  ClearingOperationFacts operation) {

    /** 阶段A可观察结果。 */
    public enum Outcome {
        /** 当前消息成功取得 PROCESSING 租约。 */
        ACQUIRED,
        /** 同一消息已经在阶段B写入成功幂等记录。 */
        ALREADY_CONSUMED,
        /** 当前动作已经清分完成或确认无需清分。 */
        ALREADY_COMPLETED,
        /** 当前动作已持久化业务延时重试，原终态消息重投不得绕过到期时间。 */
        RETRY_ALREADY_SCHEDULED,
        /** 当前动作已进入人工复核，自动消息不得再次领取。 */
        MANUAL_REVIEW_REQUIRED,
        /** 其他实例持有租约或 CAS 版本已经变化。 */
        BUSY,
        /** 延时消息的修订、重试序号或失败原因已经过期，无需再次处理。 */
        STALE_RETRY
    }

    /** @return 当前消息是否需要继续执行事务外准备和阶段B提交 */
    public boolean acquired() {
        return outcome == Outcome.ACQUIRED;
    }
}
