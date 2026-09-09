package com.scott.payment.payment.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.payment.mq.message.TransactionEventMessage;
import com.scott.payment.payment.service.TransactionQueryCacheService;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionQueryCacheInvalidationConsumerTests
 * @date : 2026-08-24 00:00
 * @email : scott_x@163.com
 * @description : 验证交易查询缓存 MQ 失效对重复投递和 Redis 失败的消费契约。
 * @status : create
 */
class TransactionQueryCacheInvalidationConsumerTests {

    /** 查询缓存失效必须跟随同一交易 FIFO 队列顺序消费生命周期事件。 */
    @Test
    void listenerContractShouldUsePaymentTransactionFifoTopicAndOrderlyMode() {
        RocketMQMessageListener listener = TransactionQueryCacheInvalidationConsumer.class
                .getAnnotation(RocketMQMessageListener.class);

        assertThat(listener.topic()).isEqualTo(MqTopic.PAYMENT_TRANSACTION_FIFO);
        assertThat(listener.consumeMode()).isEqualTo(ConsumeMode.ORDERLY);
    }

    @Test
    void shouldSafelyAdvanceGenerationForDuplicateDelivery() {
        TransactionQueryCacheService cacheService = mock(TransactionQueryCacheService.class);
        when(cacheService.advanceGeneration("merchant-1", "order-1")).thenReturn(true);
        TransactionQueryCacheInvalidationConsumer consumer =
                new TransactionQueryCacheInvalidationConsumer(cacheService);
        String payload = payload();

        consumer.onMessage(payload);
        consumer.onMessage(payload);

        verify(cacheService, times(2)).advanceGeneration("merchant-1", "order-1");
    }

    @Test
    void shouldThrowSoRocketMqRetriesWhenGenerationAdvanceFails() {
        TransactionQueryCacheService cacheService = mock(TransactionQueryCacheService.class);
        when(cacheService.advanceGeneration("merchant-1", "order-1")).thenReturn(false);
        TransactionQueryCacheInvalidationConsumer consumer =
                new TransactionQueryCacheInvalidationConsumer(cacheService);

        assertThatThrownBy(() -> consumer.onMessage(payload()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("generation advancement failed");
    }

    @Test
    void shouldInvalidateQueryCacheWhenClearingCompletes() {
        TransactionQueryCacheService cacheService = mock(TransactionQueryCacheService.class);
        when(cacheService.advanceGeneration("merchant-1", "order-1")).thenReturn(true);
        TransactionQueryCacheInvalidationConsumer consumer =
                new TransactionQueryCacheInvalidationConsumer(cacheService);

        consumer.onMessage(payload(MqTag.TRANSACTION_CLEARING_COMPLETED));

        verify(cacheService).advanceGeneration("merchant-1", "order-1");
    }

    private String payload() {
        return payload(MqTag.TRANSACTION_STATUS_CHANGED);
    }

    private String payload(String eventType) {
        TransactionEventMessage message = new TransactionEventMessage();
        message.setMessageId("message-1");
        message.setMerchantId("merchant-1");
        message.setMerchantOrderNo("order-1");
        message.setEventType(eventType);
        return JsonUtils.toJsonString(message);
    }
}
