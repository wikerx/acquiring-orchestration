package com.scott.payment.risk.mq.message;

import com.scott.payment.component.mq.message.BaseMqMessage;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskPaymentTransactionEventMessage
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 风控消费的支付交易生命周期事件最小投影。
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class RiskPaymentTransactionEventMessage extends BaseMqMessage {

    /**
     * 平台交易号，用于定位需要确认或取消的累计限额预占。
     */
    private String transactionId;

    /**
     * 触发事件的交易操作单号。
     */
    private String operationId;

    /**
     * 交易所属商户号，用于消费审计，不作为跨商户查询条件。
     */
    private String merchantId;

    /**
     * 商户订单号，用于日志追踪和人工核对。
     */
    private String merchantOrderNo;

    /**
     * 交易动作类型，例如 PAYMENT、CAPTURE 或 VOID。
     */
    private String transactionType;

    /**
     * 事件发生时的平台交易状态。
     */
    private String transactionStatus;

    /**
     * 支付核心发布的生命周期事件类型，决定确认或取消预占。
     */
    private String eventType;

    /**
     * 交易发生时间，用于分表定位和审计，精度为毫秒。
     */
    private LocalDateTime transactionDateTime;
}
