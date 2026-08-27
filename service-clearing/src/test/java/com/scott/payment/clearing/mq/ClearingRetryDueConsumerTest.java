package com.scott.payment.clearing.mq;

import com.scott.payment.clearing.application.ClearingProcessingApplicationService;
import com.scott.payment.clearing.application.ClearingProcessingResult;
import com.scott.payment.clearing.config.ClearingProperties;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.component.core.id.GlobalIdGenerator;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.ClearingRetryDueMessage;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
 * @classname : ClearingRetryDueConsumerTest
 * @date : 2026-08-26 16:40
 * @email : scott_x@163.com
 * @description : 验证清分延时消费者保留修订、重试序号、失败码和UTC投递时间，并使用独立Delay Topic并发消费。
 * @status : create
 */
class ClearingRetryDueConsumerTest {

    @Test
    void shouldConsumeRetryControlFieldsThroughApplicationService() {
        ClearingProcessingApplicationService applicationService = mock(ClearingProcessingApplicationService.class);
        GlobalIdGenerator idGenerator = mock(GlobalIdGenerator.class);
        when(idGenerator.nextId()).thenReturn("2001");
        when(applicationService.process(any(), any(), any()))
                .thenReturn(ClearingProcessingResult.STALE_RETRY_ACKNOWLEDGED);
        ClearingRetryDueConsumer consumer = new ClearingRetryDueConsumer(
                applicationService, idGenerator, new ClearingProperties(), mock(ClearingOperationalMetrics.class));
        ClearingRetryDueMessage message = message();

        consumer.onMessage(JsonUtils.toJsonString(message));

        verify(applicationService).process(
                org.mockito.ArgumentMatchers.argThat(parsed ->
                        parsed instanceof ClearingRetryDueMessage retry
                                && retry.getClearingRetryCount() == 3
                                && "SOURCE_CLEARING_PENDING".equals(retry.getRetryReasonCode())
                                && Instant.parse("2026-08-26T01:15:00.123Z").equals(retry.getDeliverAt())),
                org.mockito.ArgumentMatchers.eq("service-clearing:2001"),
                any(LocalDateTime.class));
    }

    @Test
    void listenerContractShouldUseDedicatedDelayTopicAndConcurrentMode() {
        RocketMQMessageListener listener = ClearingRetryDueConsumer.class
                .getAnnotation(RocketMQMessageListener.class);

        assertThat(listener.topic()).isEqualTo(MqTopic.PAYMENT_CLEARING_DELAY);
        assertThat(listener.selectorExpression()).isEqualTo(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        assertThat(listener.consumeMode()).isEqualTo(ConsumeMode.CONCURRENTLY);
    }

    /** 空载荷不记录正文，只按固定来源和原因累计拒绝指标并触发 Broker 重试。 */
    @Test
    void emptyPayloadShouldRecordLowCardinalityMetric() {
        ClearingOperationalMetrics metrics = mock(ClearingOperationalMetrics.class);
        ClearingRetryDueConsumer consumer = new ClearingRetryDueConsumer(
                mock(ClearingProcessingApplicationService.class), mock(GlobalIdGenerator.class),
                new ClearingProperties(), metrics);

        assertThatThrownBy(() -> consumer.onMessage(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("clearing retry due payload is empty");

        verify(metrics).recordMessageRejected("RETRY_DUE", "EMPTY");
    }

    private ClearingRetryDueMessage message() {
        ClearingRetryDueMessage message = new ClearingRetryDueMessage();
        message.setMessageId("RETRY-3");
        message.setTraceId("TRACE-1");
        message.setTransactionId("TX-1");
        message.setOperationId("OP-1");
        message.setMerchantId("M-1");
        message.setMerchantOrderNo("ORDER-1");
        message.setTransactionType("REFUND");
        message.setTransactionStatus("SUCCESS");
        message.setEventType(MqTag.TRANSACTION_CLEARING_RETRY_DUE);
        message.setTransactionDateTime(LocalDateTime.of(2026, 8, 26, 8, 30, 0, 123_000_000));
        message.setSourceEventNo("MSG-1");
        message.setExpectedClearingRevision(0);
        message.setClearingRetryCount(3);
        message.setRetryReasonCode("SOURCE_CLEARING_PENDING");
        message.setDeliverAt(Instant.parse("2026-08-26T01:15:00.123Z"));
        return message;
    }
}
