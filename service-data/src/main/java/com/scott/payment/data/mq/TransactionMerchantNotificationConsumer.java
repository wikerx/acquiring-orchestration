package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.enums.PaymentTransactionEventStatus;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import com.scott.payment.data.service.MerchantNotificationDeliveryService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionMerchantNotificationConsumer
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 交易终态事件消费者，只按消息携带的真实交易时间和交易号精确触发对应商户通知
 * @status : create
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "data.merchant-notification.mq", name = "enabled", havingValue = "true")
@RocketMQMessageListener(
        topic = MqTopic.PAYMENT_EVENT,
        consumerGroup = DataMqConsumerGroups.MERCHANT_NOTIFICATION,
        selectorExpression = MqTag.TRANSACTION_CALLBACK_PROCESSED
                + " || " + MqTag.TRANSACTION_STATUS_CHANGED,
        messageModel = MessageModel.CLUSTERING
)
public class TransactionMerchantNotificationConsumer implements RocketMQListener<String> {

    /** 商户通知投递服务，数据库版本 CAS 提供最终重复消费保护。 */
    private final MerchantNotificationDeliveryService deliveryService;

    /**
     * 创建交易终态商户通知消费者。
     *
     * @param deliveryService 商户通知投递服务
     */
    public TransactionMerchantNotificationConsumer(MerchantNotificationDeliveryService deliveryService) {
        this.deliveryService = deliveryService;
    }

    /**
     * 消费支付事件并触发商户通知。
     *
     * <p>消息本身不作为通知状态事实；重复消息由通知任务状态和版本 CAS 吸收。无效消息不访问数据库，
     * 避免无法定位分表的毒消息无限重试。</p>
     *
     * @param payload RocketMQ JSON 消息体，不允许包含卡数据或密钥
     */
    @Override
    public void onMessage(String payload) {
        long startNanos = System.nanoTime();
        PaymentTransactionEventMessage message = parseMessage(payload);
        if (message == null
                || message.getTransactionDateTime() == null
                || !StringUtils.hasText(message.getTransactionId())
                || !isTerminalEvent(message.getEventType())
                || !PaymentTransactionEventStatus.isTerminal(message.getTransactionStatus())) {
            log.warn("event: DATA_PAYMENT_EVENT_SKIPPED traceId: {} reason=messageInvalid payloadLength: {} durationMs: {}",
                    TraceContext.getTraceId(), payload == null ? 0 : payload.length(), elapsedMillis(startNanos));
            return;
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            boolean notified = deliveryService.notifyTransaction(
                    message.getTransactionDateTime(), message.getTransactionId());
            int successCount = notified ? 1 : 0;
            log.info("event: DATA_PAYMENT_EVENT_CONSUMED traceId: {} messageId: {} retryCount: {} transactionId: {} operationId: {} merchantId: {} merchantOrderNo: {} transactionType: {} eventType: {} notifyId: {} successCount: {} durationMs: {}",
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
     * 只接受已经在 Payment 终态事务内写入的事件，创建事件不能驱动商户通知。
     *
     * @param eventType MQ Tag 对应的事件类型
     * @return true 表示通知任务在事件提交前已经完成激活
     */
    private boolean isTerminalEvent(String eventType) {
        return MqTag.TRANSACTION_CALLBACK_PROCESSED.equals(eventType)
                || MqTag.TRANSACTION_STATUS_CHANGED.equals(eventType);
    }

    /**
     * 安全解析交易事件；畸形 JSON 只记录载荷长度和异常类型，不输出消息原文。
     *
     * @param payload RocketMQ JSON 消息体
     * @return 可解析消息，格式非法时返回空
     */
    private PaymentTransactionEventMessage parseMessage(String payload) {
        if (!StringUtils.hasText(payload)) {
            return null;
        }
        try {
            return JsonUtils.parseObject(payload, PaymentTransactionEventMessage.class);
        } catch (RuntimeException exception) {
            log.warn("event: DATA_PAYMENT_EVENT_PARSE_FAILED traceId: {} payloadLength: {} exceptionType: {}",
                    TraceContext.getTraceId(), payload.length(), exception.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 计算单条 MQ 消息处理耗时。
     *
     * @param startNanos 单调时钟起始值
     * @return 耗时毫秒数
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
