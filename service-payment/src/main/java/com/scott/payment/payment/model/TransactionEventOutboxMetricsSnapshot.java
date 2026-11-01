package com.scott.payment.payment.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单季度交易 MQ Outbox 运维聚合快照，只包含固定状态计数和最老积压时间，不包含交易或消息业务标识。
 */
@Data
public class TransactionEventOutboxMetricsSnapshot {

    /**
     * INIT 状态事件数量。
     */
    private Long initCount;
    /**
     * PROCESSING 状态事件数量。
     */
    private Long processingCount;
    /**
     * FAILED 待重试事件数量。
     */
    private Long failedCount;
    /**
     * 重试耗尽后进入 Outbox CLOSED 的事件数量。
     */
    private Long closedCount;
    /**
     * INIT、PROCESSING、FAILED 中最早事件的创建时间。
     */
    private LocalDateTime oldestPendingTime;
}
