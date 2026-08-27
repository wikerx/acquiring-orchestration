package com.scott.payment.clearing.service;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReserveReleaseService
 * @date : 2026-08-26 18:36
 * @email : scott_x@163.com
 * @description : 单条保证金到期释放事务边界；锁定原标签币种负债并追加独立RELEASE事实和结算候选。
 * @status : create
 */
public interface ReserveReleaseService {

    /** 保证金释放处理结果。 */
    enum ReserveReleaseOutcome {
        RELEASED,
        ALREADY_FINAL,
        NOT_DUE
    }

    /**
     * 处理一条扫描候选，数据库状态和版本始终优先于扫描快照。
     *
     * @param reserveStateId 扫描得到的保证金状态业务号
     * @param originalTransactionId 原支付动作交易号
     * @param originalTransactionDateTime 原支付季度分片时间
     * @param releaseInstant 本轮统一释放时点
     * @return 释放结果、稳定动作号和来源修订
     */
    ReserveReleaseResult release(String reserveStateId,
                                 String originalTransactionId,
                                 LocalDateTime originalTransactionDateTime,
                                 Instant releaseInstant);

    /** 单条释放结果；未形成新事实时动作号为空、修订为零。 */
    record ReserveReleaseResult(ReserveReleaseOutcome outcome,
                                String releaseTransactionId,
                                int sourceRevision) {
    }
}
