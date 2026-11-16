package com.scott.payment.component.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 人工重发商户终态回调的 MQ 契约，只携带精确分片定位和审计标识，不携带回调密文、密钥或 JWT。
 *
 * <p>{@code notifyId} 与 {@code merchantId} 是商户端重发使用的兼容扩展字段；管理端历史消息可以不传，
 * 消费端继续按交易号和分片时间定位。商户端消息必须同时传入两者，防止同一交易存在多条通知任务时
 * 误投其他任务，或跨商户触发回调。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MerchantNotificationRetryMessage extends BaseMqMessage {

    /**
     * 平台交易 ID，不允许为空。
     */
    private String transactionId;

    /**
     * 商户通知任务号；商户端重发必填，管理端兼容消息可为空。
     */
    private String notifyId;

    /**
     * 通知任务所属商户号；商户端重发必填，管理端兼容消息可为空。
     */
    private String merchantId;

    /**
     * 交易业务时间，用于精确定位季度分表，不允许为空。
     */
    private LocalDateTime transactionDateTime;

    /**
     * 事件类型，必须等于人工重发 RocketMQ Tag。
     */
    private String eventType;

    /**
     * 人工重发请求唯一号，用于关联操作日志和 Outbox，不包含商户密钥。
     */
    private String requestId;

    /**
     * 发起人工重发的操作人摘要，仅用于审计。
     */
    private String requestedBy;
}
