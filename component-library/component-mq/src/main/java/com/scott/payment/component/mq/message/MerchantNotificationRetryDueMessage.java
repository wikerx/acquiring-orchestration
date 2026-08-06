package com.scott.payment.component.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationRetryDueMessage
 * @date : 2026-08-06 12:34
 * @email : scott_x@163.com
 * @description : 自动商户通知重试到期事件，只携带分片和状态 CAS 信息，不承载商户回调 Header、Body 或密钥
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class MerchantNotificationRetryDueMessage extends BaseMqMessage {

    /** 平台通知任务号，不允许为空。 */
    private String notifyId;

    /** 平台交易号，不允许为空。 */
    private String transactionId;

    /** 交易业务时间，用于精确路由季度分表。 */
    private LocalDateTime transactionDateTime;

    /** 通知失败状态提交后的任务版本，用于拒绝重复或过期消息。 */
    private Integer expectedVersion;

    /** 本消息计划触发的回调尝试序号。 */
    private Integer attemptNo;

    /** RocketMQ 最早投递时间，按平台交易时区解释。 */
    private LocalDateTime deliverAt;

    /** 事件类型，必须等于自动重试到期 Tag。 */
    private String eventType;
}
