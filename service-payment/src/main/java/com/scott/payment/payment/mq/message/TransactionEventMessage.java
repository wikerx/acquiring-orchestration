package com.scott.payment.payment.mq.message;

import com.scott.payment.component.mq.message.BaseMqMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionEventMessage
 * @date : 2026-07-14 21:46
 * @email : scott_x@163.com
 * @description : 收单交易事件消息体，位于 service-payment 消息层，携带交易分表时间和平台标识供通知、对账等异步消费者定位数据。
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class TransactionEventMessage extends BaseMqMessage {

    /**
     * 平台当前交易唯一标识。
     */
    private String transactionId;

    /**
     * 平台内部生命周期关联标识。
     */
    private String operationId;

    /**
     * 平台商户号。
     */
    private String merchantId;

    /**
     * 商户订单号。
     */
    private String merchantOrderNo;

    /**
     * 交易类型，对齐 transaction_type 字典。
     */
    private String transactionType;

    /**
     * 交易状态，对齐 transaction_status 字典。
     */
    private String transactionStatus;

    /**
     * 交易事件类型，对齐 transaction_event_outbox.event_type。
     */
    private String eventType;

    /**
     * 商户通知任务 ID；没有创建通知任务或渠道不需要商户通知时为空。
     */
    private String notifyId;

    /**
     * 交易业务时间，用于定位 transaction_merchant_notification 等物理分表。
     */
    private LocalDateTime transactionDateTime;
}
