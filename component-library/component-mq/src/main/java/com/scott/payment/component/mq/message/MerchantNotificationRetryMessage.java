package com.scott.payment.component.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationRetryMessage
 * @date : 2026-08-04 13:40
 * @email : scott_x@163.com
 * @description : 管理后台人工重发商户终态回调的 MQ 契约，只携带精确分片定位和审计标识，不携带回调密文、密钥或 JWT
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MerchantNotificationRetryMessage extends BaseMqMessage {

    /** 平台交易 ID，不允许为空。 */
    private String transactionId;

    /** 交易业务时间，用于精确定位季度分表，不允许为空。 */
    private LocalDateTime transactionDateTime;

    /** 事件类型，必须等于人工重发 RocketMQ Tag。 */
    private String eventType;

    /** 管理端请求唯一号，用于关联操作日志和 Outbox，不包含商户密钥。 */
    private String requestId;

    /** 发起人工重发的后台操作人摘要，仅用于审计。 */
    private String requestedBy;
}
