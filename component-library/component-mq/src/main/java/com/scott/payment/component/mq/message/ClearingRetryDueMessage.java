package com.scott.payment.component.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.Instant;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingRetryDueMessage
 * @date : 2026-08-26 16:00
 * @email : scott_x@163.com
 * @description : 清分业务延时重试公共契约，只携带动作身份、CAS预期和调度控制字段，不携带金额、费率、汇率或持卡人数据。
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ClearingRetryDueMessage extends PaymentTransactionEventMessage {

    /** 首次触发本动作清分的交易终态事件号，用于跨多次重试审计。 */
    private String sourceEventNo;

    /** 生成重试消息时的有效清分修订号，消费时仅用于拒绝过期消息。 */
    private Integer expectedClearingRevision;

    /** 本消息对应的清分业务重试序号，从 1 开始。 */
    private Integer clearingRetryCount;

    /** 触发本次业务延时的稳定清分失败码。 */
    private String retryReasonCode;

    /** RocketMQ 5.x 最早投递时间，始终使用 UTC 时间点。 */
    private Instant deliverAt;
}
