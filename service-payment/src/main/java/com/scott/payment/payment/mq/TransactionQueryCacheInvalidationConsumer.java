package com.scott.payment.payment.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.payment.mq.message.TransactionEventMessage;
import com.scott.payment.payment.service.TransactionQueryCacheService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionQueryCacheInvalidationConsumer
 * @date : 2026-08-24 00:00
 * @email : scott_x@163.com
 * @description : 交易查询缓存失效消费者，消费有序生命周期事件并以 generation 推进补偿同步写路径的 Redis 失效。
 * @status : create
 */
@Slf4j
@Component
@ConditionalOnProperty(
        prefix = "payment.transaction.query-cache",
        name = "invalidation-mq-enabled",
        havingValue = "true",
        matchIfMissing = true)
@RocketMQMessageListener(
        topic = MqTopic.PAYMENT_EVENT,
        consumerGroup = "service-payment-transaction-query-cache",
        selectorExpression = "TRANSACTION_CREATED || TRANSACTION_STATUS_CHANGED || TRANSACTION_CALLBACK_PROCESSED",
        messageModel = MessageModel.CLUSTERING)
public class TransactionQueryCacheInvalidationConsumer implements RocketMQListener<String> {

    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            MqTag.TRANSACTION_CREATED,
            MqTag.TRANSACTION_STATUS_CHANGED,
            MqTag.TRANSACTION_CALLBACK_PROCESSED
    );

    private final TransactionQueryCacheService transactionQueryCacheService;

    /** @param transactionQueryCacheService 订单级查询缓存 generation 服务 */
    public TransactionQueryCacheInvalidationConsumer(TransactionQueryCacheService transactionQueryCacheService) {
        this.transactionQueryCacheService = transactionQueryCacheService;
    }

    /**
     * 消费交易生命周期事件；重复投递可重复推进 generation，不能覆盖数据库事实。
     *
     * @param payload 不含卡认证数据的交易事件 JSON
     */
    @Override
    public void onMessage(String payload) {
        TransactionEventMessage message = parse(payload);
        if (!valid(message)) {
            throw new IllegalArgumentException("transaction query cache invalidation message is invalid");
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            boolean invalidated = transactionQueryCacheService.advanceGeneration(
                    message.getMerchantId(), message.getMerchantOrderNo());
            if (!invalidated) {
                throw new IllegalStateException("transaction query cache generation advancement failed");
            }
            log.info("event: TRANSACTION_QUERY_CACHE_INVALIDATED traceId: {} messageId: {} eventType: {}",
                    TraceContext.getTraceId(), message.getMessageId(), message.getEventType());
        } finally {
            TraceContext.clear();
        }
    }

    private TransactionEventMessage parse(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("transaction query cache invalidation payload is empty");
        }
        try {
            return JsonUtils.parseObject(payload, TransactionEventMessage.class);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("transaction query cache invalidation payload is invalid", exception);
        }
    }

    private boolean valid(TransactionEventMessage message) {
        return message != null
                && StringUtils.hasText(message.getMessageId())
                && StringUtils.hasText(message.getMerchantId())
                && StringUtils.hasText(message.getMerchantOrderNo())
                && SUPPORTED_EVENTS.contains(message.getEventType());
    }
}
