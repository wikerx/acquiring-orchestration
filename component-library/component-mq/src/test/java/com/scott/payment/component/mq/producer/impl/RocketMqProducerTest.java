package com.scott.payment.component.mq.producer.impl;

import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.message.BaseMqMessage;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RocketMqProducerTest
 * @date : 未确认
 * @email : scott_x@163.com
 * @description : Rocket MQ Producer Test 消息投递组件，位于 公共组件库，补齐消息标识、traceId、重试次数和业务载荷后发送 MQ。
 * @status : create
 */
class RocketMqProducerTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void shouldFillTraceMetadataEvenWhenRocketMqTemplateIsUnavailable() {
        TraceContext.setTraceId("trace-mq-001");
        ObjectProvider<RocketMQTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        RocketMqProducer producer = new RocketMqProducer(provider);
        BaseMqMessage message = new BaseMqMessage();

        producer.send("payment-event", "created", message);

        assertThat(message.getMessageId()).isNotBlank();
        assertThat(message.getCreatedAt()).isNotNull();
        assertThat(message.getTraceId()).isEqualTo("trace-mq-001");
        assertThat(message.getRetryCount()).isZero();
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldPropagateTraceAndRetryCountToRocketMqHeaders() {
        ObjectProvider<RocketMQTemplate> provider = mock(ObjectProvider.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        when(provider.getIfAvailable()).thenReturn(rocketMQTemplate);
        RocketMqProducer producer = new RocketMqProducer(provider);
        BaseMqMessage message = new BaseMqMessage();
        message.setMessageId("message-001");
        message.setTraceId("trace-from-body");
        message.setRetryCount(2);

        producer.send("payment-event", "created", message);

        org.mockito.ArgumentCaptor<Message<String>> captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSend(eq("payment-event:created"), captor.capture());
        Message<String> rocketMessage = captor.getValue();
        assertThat(rocketMessage.getHeaders().get(TraceContext.TRACE_ID_HEADER)).isEqualTo("trace-from-body");
        assertThat(rocketMessage.getHeaders().get("retryCount")).isEqualTo(2);
        assertThat(rocketMessage.getHeaders().get("messageId")).isEqualTo("message-001");
    }

    /** 未来投递时间必须使用 RocketMQ 5.x 绝对定时消息能力。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldSendMessageAtAbsoluteDeliveryTime() {
        ObjectProvider<RocketMQTemplate> provider = mock(ObjectProvider.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        when(provider.getIfAvailable()).thenReturn(rocketMQTemplate);
        RocketMqProducer producer = new RocketMqProducer(provider);
        BaseMqMessage message = new BaseMqMessage();
        message.setMessageId("message-delayed-001");
        Instant deliverAt = Instant.now().plusSeconds(300);

        producer.sendAt("payment-event", "retry-due", message, deliverAt);

        org.mockito.ArgumentCaptor<Message<String>> captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSendDeliverTimeMills(
                eq("payment-event:retry-due"), captor.capture(), eq(deliverAt.toEpochMilli()));
        assertThat(captor.getValue().getHeaders().get("messageId")).isEqualTo("message-delayed-001");
    }
}
