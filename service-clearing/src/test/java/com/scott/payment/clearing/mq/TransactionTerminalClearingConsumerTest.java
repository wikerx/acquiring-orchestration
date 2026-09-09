package com.scott.payment.clearing.mq;

import com.scott.payment.clearing.application.ClearingProcessingApplicationService;
import com.scott.payment.clearing.application.ClearingProcessingResult;
import com.scott.payment.clearing.config.ClearingProperties;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionTerminalClearingConsumerTest
 * @date : 2026-08-26 16:40
 * @email : scott_x@163.com
 * @description : 验证交易终态消费者使用顺序消费并把完整动作身份交给清分应用编排。
 * @status : create
 */
class TransactionTerminalClearingConsumerTest {

    @Test
    void shouldConsumeTerminalEventThroughApplicationService() {
        ClearingProcessingApplicationService applicationService = mock(ClearingProcessingApplicationService.class);
        GlobalIdGenerator idGenerator = mock(GlobalIdGenerator.class);
        when(idGenerator.nextId()).thenReturn("1001");
        when(applicationService.process(any(), any(), any()))
                .thenReturn(ClearingProcessingResult.COMPLETED);
        TransactionTerminalClearingConsumer consumer = new TransactionTerminalClearingConsumer(
                applicationService, idGenerator, new ClearingProperties(), mock(ClearingOperationalMetrics.class));
        PaymentTransactionEventMessage message = message();

        consumer.onMessage(JsonUtils.toJsonString(message));

        verify(applicationService).process(
                org.mockito.ArgumentMatchers.argThat(parsed ->
                        "TX-1".equals(parsed.getTransactionId())
                                && TRANSACTION_TIME.equals(parsed.getTransactionDateTime())),
                org.mockito.ArgumentMatchers.eq("service-clearing:1001"),
                any(LocalDateTime.class));
    }

    @Test
    void listenerContractShouldUsePaymentTransactionFifoTopicAndOrderlyMode() {
        RocketMQMessageListener listener = TransactionTerminalClearingConsumer.class
                .getAnnotation(RocketMQMessageListener.class);

        assertThat(listener.topic()).isEqualTo(MqTopic.PAYMENT_TRANSACTION_FIFO);
        assertThat(listener.selectorExpression()).isEqualTo(MqTag.TRANSACTION_STATUS_CHANGED);
        assertThat(listener.consumeMode()).isEqualTo(ConsumeMode.ORDERLY);
    }

    /** 畸形 JSON 不记录原文，只按固定来源和原因累计拒绝指标并触发 Broker 重试。 */
    @Test
    void malformedPayloadShouldRecordLowCardinalityMetric() {
        ClearingOperationalMetrics metrics = mock(ClearingOperationalMetrics.class);
        TransactionTerminalClearingConsumer consumer = new TransactionTerminalClearingConsumer(
                mock(ClearingProcessingApplicationService.class), mock(GlobalIdGenerator.class),
                new ClearingProperties(), metrics);

        assertThatThrownBy(() -> consumer.onMessage("{invalid-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("transaction terminal clearing payload is invalid");

        verify(metrics).recordMessageRejected("TERMINAL", "DESERIALIZATION");
    }

    private static final LocalDateTime TRANSACTION_TIME =
            LocalDateTime.of(2026, 8, 26, 8, 30, 0, 123_000_000);

    private PaymentTransactionEventMessage message() {
        PaymentTransactionEventMessage message = new PaymentTransactionEventMessage();
        message.setMessageId("MSG-1");
        message.setTraceId("TRACE-1");
        message.setTransactionId("TX-1");
        message.setOperationId("OP-1");
        message.setMerchantId("M-1");
        message.setMerchantOrderNo("ORDER-1");
        message.setTransactionType("PAYMENT");
        message.setTransactionStatus("SUCCESS");
        message.setEventType(MqTag.TRANSACTION_STATUS_CHANGED);
        message.setTransactionDateTime(TRANSACTION_TIME);
        return message;
    }
}
