package com.scott.payment.component.mq.producer.impl;

import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.message.BaseMqMessage;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.Message;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
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
    void shouldRejectDeliveryWhenRocketMqTemplateIsUnavailable() {
        TraceContext.setTraceId("trace-mq-001");
        ObjectProvider<RocketMQTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        RocketMqProducer producer = new RocketMqProducer(provider);
        BaseMqMessage message = new BaseMqMessage();

        assertThatIllegalStateException()
                .isThrownBy(() -> producer.send("payment-event", "created", message))
                .withMessage("RocketMQTemplate is not ready");

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
        when(rocketMQTemplate.syncSend(eq("payment-event:created"), org.mockito.ArgumentMatchers.any(Message.class)))
                .thenReturn(sendOk());
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
        when(rocketMQTemplate.syncSendDeliverTimeMills(
                eq("payment-event:retry-due"), org.mockito.ArgumentMatchers.any(Message.class),
                eq(deliverAt.toEpochMilli()))).thenReturn(sendOk());

        producer.sendAt("payment-event", "retry-due", message, deliverAt);

        org.mockito.ArgumentCaptor<Message<String>> captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSendDeliverTimeMills(
                eq("payment-event:retry-due"), captor.capture(), eq(deliverAt.toEpochMilli()));
        assertThat(captor.getValue().getHeaders().get("messageId")).isEqualTo("message-delayed-001");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRejectNonSuccessfulBrokerResult() {
        ObjectProvider<RocketMQTemplate> provider = mock(ObjectProvider.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        when(provider.getIfAvailable()).thenReturn(rocketMQTemplate);
        SendResult result = new SendResult();
        result.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
        when(rocketMQTemplate.syncSend(eq("payment-event:created"), org.mockito.ArgumentMatchers.any(Message.class)))
                .thenReturn(result);
        RocketMqProducer producer = new RocketMqProducer(provider);

        assertThatIllegalStateException()
                .isThrownBy(() -> producer.send("payment-event", "created", new BaseMqMessage()))
                .withMessageContaining("sendStatus=FLUSH_DISK_TIMEOUT");
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSendOrderlyByBusinessGroup() {
        ObjectProvider<RocketMQTemplate> provider = mock(ObjectProvider.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        when(provider.getIfAvailable()).thenReturn(rocketMQTemplate);
        when(rocketMQTemplate.syncSendOrderly(
                eq("payment-event:updated"), org.mockito.ArgumentMatchers.any(Message.class), eq("TXN-001")))
                .thenReturn(sendOk());
        RocketMqProducer producer = new RocketMqProducer(provider);

        producer.sendOrderly("payment-event", "updated", new BaseMqMessage(), "TXN-001");

        verify(rocketMQTemplate).syncSendOrderly(
                eq("payment-event:updated"), org.mockito.ArgumentMatchers.any(Message.class), eq("TXN-001"));
    }

    /** Outbox 冻结 JSON 的字段顺序和格式必须保持不变，顺序键只参与队列选择。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldSendFrozenJsonOrderlyWithoutReserialization() {
        ObjectProvider<RocketMQTemplate> provider = mock(ObjectProvider.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        when(provider.getIfAvailable()).thenReturn(rocketMQTemplate);
        when(rocketMQTemplate.syncSendOrderly(
                eq("payment-event:TRANSACTION_STATUS_CHANGED"),
                org.mockito.ArgumentMatchers.any(Message.class), eq("operation-001")))
                .thenReturn(sendOk());
        RocketMqProducer producer = new RocketMqProducer(provider);
        String payloadJson = "{ \"z\": 1, \"a\": \"001\" }";

        producer.sendSerializedOrderly(
                "payment-event", "TRANSACTION_STATUS_CHANGED", "message-ordered-001",
                "trace-ordered-001", 3, payloadJson, "operation-001");

        org.mockito.ArgumentCaptor<Message<String>> captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSendOrderly(
                eq("payment-event:TRANSACTION_STATUS_CHANGED"), captor.capture(), eq("operation-001"));
        assertThat(captor.getValue().getPayload()).isEqualTo(payloadJson);
        assertThat(captor.getValue().getHeaders().get(TraceContext.TRACE_ID_HEADER))
                .isEqualTo("trace-ordered-001");
        assertThat(captor.getValue().getHeaders().get("retryCount")).isEqualTo(3);
        assertThat(captor.getValue().getHeaders().get("messageId")).isEqualTo("message-ordered-001");
    }

    /** Outbox 冻结 JSON 定时消息必须使用 Broker 绝对投递时间且不得重写载荷。 */
    @Test
    @SuppressWarnings("unchecked")
    void shouldSendFrozenJsonAtAbsoluteDeliveryTime() {
        ObjectProvider<RocketMQTemplate> provider = mock(ObjectProvider.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        when(provider.getIfAvailable()).thenReturn(rocketMQTemplate);
        Instant deliverAt = Instant.now().plusSeconds(300);
        when(rocketMQTemplate.syncSendDeliverTimeMills(
                eq("acquiring_payment_clearing_delay_topic:TRANSACTION_CLEARING_RETRY_DUE"),
                org.mockito.ArgumentMatchers.any(Message.class), eq(deliverAt.toEpochMilli())))
                .thenReturn(sendOk());
        RocketMqProducer producer = new RocketMqProducer(provider);
        String payloadJson = "{\n  \"retry\": true,\n  \"revision\": 2\n}";

        producer.sendSerializedAt(
                "acquiring_payment_clearing_delay_topic", "TRANSACTION_CLEARING_RETRY_DUE",
                "message-scheduled-001", "trace-scheduled-001", 4, payloadJson, deliverAt);

        org.mockito.ArgumentCaptor<Message<String>> captor = org.mockito.ArgumentCaptor.forClass(Message.class);
        verify(rocketMQTemplate).syncSendDeliverTimeMills(
                eq("acquiring_payment_clearing_delay_topic:TRANSACTION_CLEARING_RETRY_DUE"),
                captor.capture(), eq(deliverAt.toEpochMilli()));
        assertThat(captor.getValue().getPayload()).isEqualTo(payloadJson);
        assertThat(captor.getValue().getHeaders().get(TraceContext.TRACE_ID_HEADER))
                .isEqualTo("trace-scheduled-001");
        assertThat(captor.getValue().getHeaders().get("retryCount")).isEqualTo(4);
        assertThat(captor.getValue().getHeaders().get("messageId")).isEqualTo("message-scheduled-001");
    }

    private SendResult sendOk() {
        SendResult result = new SendResult();
        result.setSendStatus(SendStatus.SEND_OK);
        return result;
    }
}
