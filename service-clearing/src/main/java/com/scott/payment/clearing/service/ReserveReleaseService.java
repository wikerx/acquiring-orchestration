package com.scott.payment.clearing.service;

import java.time.Instant;
import java.time.LocalDateTime;

/**
 * 单条保证金到期释放事务边界；锁定原标签币种负债并追加独立RELEASE事实和结算候选。
 */
public interface ReserveReleaseService {

    /**
     * 保证金释放处理结果。
     */
    enum ReserveReleaseOutcome {
        /**
         * 本次调用已追加释放事实并更新保证金余额。
         */
        RELEASED,
        /**
         * 保证金已全部返还或释放，重复调用无需创建新事实。
         */
        ALREADY_FINAL,
        /**
         * 当前时点尚未达到冻结释放日期。
         */
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

    /**
     * 单条释放结果；未形成新事实时动作号为空、修订为零。
     */
    record ReserveReleaseResult(ReserveReleaseOutcome outcome,
                                String releaseTransactionId,
                                int sourceRevision) {
    }
}
