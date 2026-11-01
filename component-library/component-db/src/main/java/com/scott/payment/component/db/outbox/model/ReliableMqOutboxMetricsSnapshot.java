package com.scott.payment.component.db.outbox.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通用可靠 MQ Outbox 运维聚合快照，只包含低基数状态计数和最老积压时间，不包含消息业务标识或载荷。
 */
@Data
public class ReliableMqOutboxMetricsSnapshot {

    /**
     * INIT 状态消息数量。
     */
    private Long initCount;
    /**
     * PROCESSING 状态消息数量。
     */
    private Long processingCount;
    /**
     * RETRY_WAIT 状态消息数量。
     */
    private Long retryWaitCount;
    /**
     * 重试耗尽后进入 CLOSED 的消息数量。
     */
    private Long closedCount;
    /**
     * INIT、PROCESSING、RETRY_WAIT 中最早消息的创建时间。
     */
    private LocalDateTime oldestPendingTime;
}
