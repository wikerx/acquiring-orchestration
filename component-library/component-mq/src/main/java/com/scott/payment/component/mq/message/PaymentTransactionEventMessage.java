package com.scott.payment.component.mq.message;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionEventMessage
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : 收单交易公共事件契约，供支付生产者、风控投影和 service-data 商户通知消费者共享，不携带卡数据、密钥或渠道凭据
 * @status : create
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class PaymentTransactionEventMessage extends BaseMqMessage {

    /** 平台当前交易 ID，不允许为空。 */
    private String transactionId;

    /** 平台交易生命周期操作 ID，允许为空。 */
    private String operationId;

    /** 平台商户号，允许为空但不得由消费者据此替代数据库归属校验。 */
    private String merchantId;

    /** 商户订单号，允许为空，仅用于审计追踪。 */
    private String merchantOrderNo;

    /** 交易类型编码，允许为空。 */
    private String transactionType;

    /** 平台交易状态编码，允许为空，数据库仍是最终事实来源。 */
    private String transactionStatus;

    /** 事件类型，必须与 RocketMQ Tag 保持一致。 */
    private String eventType;

    /** 商户通知任务 ID；没有创建通知任务时允许为空。 */
    private String notifyId;

    /** 交易业务时间，用于定位季度物理分表，不允许为空。 */
    private LocalDateTime transactionDateTime;
}
