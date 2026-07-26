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
 * @description : Transaction Merchant Notification Consumer 消息消费组件，位于 支付核心服务，解析 MQ 消息、绑定 traceId 和重试次数，并触发后续业务处理。
 * @status : create
 */
public class TransactionMerchantNotificationConsumer implements RocketMQListener<String> {

    /**
     * DEFAULT BATCH LIMIT，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：由关联 currency 字段决定；格式：decimal 金额字符串或 BigDecimal；不允许为空；非敏感字段。
     * 取值范围：金额不得为负，交易金额通常必须大于 0；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int DEFAULT_BATCH_LIMIT = 20;

    /**
     * notification Service 依赖，用于 Transaction Merchant Notification Consumer 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
        long startNanos = System.nanoTime();
        TransactionEventMessage message = JsonUtils.parseObject(payload, TransactionEventMessage.class);
        if (message == null || message.getTransactionDateTime() == null) {
            log.warn("event: PAYMENT_EVENT_CONSUME_SKIP stage=MQ_CONSUME traceId: {} reason=messageInvalid payloadLength: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    payload == null ? 0 : payload.length(),
                    elapsedMillis(startNanos));
            return;
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            log.info("event: PAYMENT_EVENT_CONSUME_START stage=MQ_CONSUME traceId: {} messageId: {} retryCount: {} transactionId: {} operationId: {} merchantId: {} merchantOrderNo: {} transactionType: {} eventType: {} notifyId: {}",
                    TraceContext.getTraceId(),
                    message.getMessageId(),
                    message.getRetryCount(),
                    message.getTransactionId(),
                    message.getOperationId(),
                    message.getMerchantId(),
                    message.getMerchantOrderNo(),
                    message.getTransactionType(),
                    message.getEventType(),
                    message.getNotifyId());
            boolean notified = notificationService.notifyTransaction(message.getTransactionDateTime(), message.getTransactionId());
            int successCount = notified ? 1 : notificationService.notifyDue(message.getTransactionDateTime(), DEFAULT_BATCH_LIMIT);
            log.info("event: PAYMENT_EVENT_CONSUME_END stage=MQ_CONSUME traceId: {} messageId: {} retryCount: {} transactionId: {} operationId: {} merchantId: {} merchantOrderNo: {} transactionType: {} eventType: {} notifyId: {} successCount: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    message.getMessageId(),
                    message.getRetryCount(),
                    message.getTransactionId(),
                    message.getOperationId(),
                    message.getMerchantId(),
                    message.getMerchantOrderNo(),
                    message.getTransactionType(),
                    message.getEventType(),
                    message.getNotifyId(),
                    successCount,
                    elapsedMillis(startNanos));
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 计算单条 MQ 消息从反序列化到业务处理结束的耗时。
     *
     * @param startNanos System.nanoTime 起始值
     * @return 耗时毫秒数
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
