package com.scott.payment.payment.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.payment.mq.message.TransactionEventMessage;
import com.scott.payment.payment.service.TransactionMerchantNotificationService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantNotificationConsumer
 * @date : 2026-07-14 21:48
 * @email : scott_x@163.com
 * @description : 商户通知交易事件消费者，位于 service-payment 消息层，消费交易终态事件并触发到期商户通知任务。
 * @status : create
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "payment.transaction.merchant-notification.mq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqTopic.PAYMENT_EVENT,
        consumerGroup = TransactionMqConstants.MERCHANT_NOTIFICATION_CONSUMER_GROUP,
        selectorExpression = TransactionMqConstants.TRANSACTION_CREATED_TAG
                + " || "
                + TransactionMqConstants.TRANSACTION_CALLBACK_PROCESSED_TAG,
        messageModel = MessageModel.CLUSTERING
)
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantNotificationConsumer
 * @date : 2026-07-14 21:48
 * @email : scott_x@163.com
 * @description : TransactionMerchantNotificationConsumer 消息消费组件，用于解析 MQ 消息、绑定链路上下文并触发后续处理，位于 支付核心服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class TransactionMerchantNotificationConsumer implements RocketMQListener<String> {

    /**
     * DEFAULT BATCH LIMIT 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int DEFAULT_BATCH_LIMIT = 20;

    /**
     * notification Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final TransactionMerchantNotificationService notificationService;

    /**
     * 创建商户通知交易事件消费者。
     *
     * @param notificationService 商户通知服务
     */
    public TransactionMerchantNotificationConsumer(TransactionMerchantNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * 消费交易事件并触发同分表到期通知。
     *
     * @param payload 交易事件 JSON
     */
    @Override
    public void onMessage(String payload) {
        TransactionEventMessage message = JsonUtils.parseObject(payload, TransactionEventMessage.class);
        if (message == null || message.getTransactionDateTime() == null) {
            log.warn("event: PAYMENT_EVENT_CONSUME_SKIP reason=messageInvalid payloadLength: {}",
                    payload == null ? 0 : payload.length());
            return;
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            log.info("event: PAYMENT_EVENT_CONSUME_START messageId: {} retryCount: {} transactionId: {} operationId: {} eventType: {} notifyId: {}",
                    message.getMessageId(),
                    message.getRetryCount(),
                    message.getTransactionId(),
                    message.getOperationId(),
                    message.getEventType(),
                    message.getNotifyId());
            boolean notified = notificationService.notifyTransaction(message.getTransactionDateTime(), message.getTransactionId());
            int successCount = notified ? 1 : notificationService.notifyDue(message.getTransactionDateTime(), DEFAULT_BATCH_LIMIT);
            log.info("event: PAYMENT_EVENT_CONSUME_END messageId: {} retryCount: {} transactionId: {} eventType: {} notifyId: {} successCount: {}",
                    message.getMessageId(),
                    message.getRetryCount(),
                    message.getTransactionId(),
                    message.getEventType(),
                    message.getNotifyId(),
                    successCount);
        } finally {
            TraceContext.clear();
        }
    }
}
