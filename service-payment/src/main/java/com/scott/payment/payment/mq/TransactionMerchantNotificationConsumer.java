package com.scott.payment.payment.mq;

import com.scott.payment.component.core.json.JsonUtils;
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
public class TransactionMerchantNotificationConsumer implements RocketMQListener<String> {

    private static final int DEFAULT_BATCH_LIMIT = 20;

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
            log.warn("交易事件消息缺少分表时间，已跳过商户通知触发，payload：{}", payload);
            return;
        }
        boolean notified = notificationService.notifyTransaction(message.getTransactionDateTime(), message.getTransactionId());
        int successCount = notified ? 1 : notificationService.notifyDue(message.getTransactionDateTime(), DEFAULT_BATCH_LIMIT);
        log.info("交易事件触发商户通知完成，transactionId：{}，eventType：{}，notifyId：{}，successCount：{}",
                message.getTransactionId(),
                message.getEventType(),
                message.getNotifyId(),
                successCount);
    }
}
