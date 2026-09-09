package com.scott.payment.payment.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionEventOutboxMetricsSnapshot
 * @date : 2026-08-24 00:00
 * @email : scott_x@163.com
 * @description : 单季度交易 MQ Outbox 运维聚合快照，只包含固定状态计数和最老积压时间，不包含交易或消息业务标识。
 * @status : create
 */
@Data
public class TransactionEventOutboxMetricsSnapshot {

    /** INIT 状态事件数量。 */
    private Long initCount;
    /** PROCESSING 状态事件数量。 */
    private Long processingCount;
    /** FAILED 待重试事件数量。 */
    private Long failedCount;
    /** 重试耗尽后进入 Outbox CLOSED 的事件数量。 */
    private Long closedCount;
    /** INIT、PROCESSING、FAILED 中最早事件的创建时间。 */
    private LocalDateTime oldestPendingTime;
}
