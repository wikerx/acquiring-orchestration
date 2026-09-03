package com.scott.payment.risk.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.risk.domain.FrequencySuccessReservationTransitionSummary;
import com.scott.payment.risk.mq.message.RiskPaymentTransactionEventMessage;
import com.scott.payment.risk.service.FrequencySuccessReservationService;
import org.apache.rocketmq.spring.annotation.ConsumeMode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : FrequencySuccessReservationPaymentEventConsumerTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 支付终态驱动频控成功名额确认和释放的消费者测试。
 * @status : create
 */
class FrequencySuccessReservationPaymentEventConsumerTests {

    /** 成功名额状态机必须顺序消费交易生命周期 FIFO Topic。 */
    @Test
    void listenerContractShouldUsePaymentTransactionFifoTopicAndOrderlyMode() {
        RocketMQMessageListener listener = FrequencySuccessReservationPaymentEventConsumer.class
                .getAnnotation(RocketMQMessageListener.class);

        assertThat(listener.topic()).isEqualTo(MqTopic.PAYMENT_TRANSACTION_FIFO);
        assertThat(listener.consumeMode()).isEqualTo(ConsumeMode.ORDERLY);
    }

    @Test
    void shouldConfirmSuccessReleaseFailureAndIgnorePending() {
        FrequencySuccessReservationService service = mock(FrequencySuccessReservationService.class);
        when(service.confirm("M001", "TX001"))
                .thenReturn(FrequencySuccessReservationTransitionSummary.empty());
        when(service.release("M001", "TX001"))
                .thenReturn(FrequencySuccessReservationTransitionSummary.empty());
        FrequencySuccessReservationPaymentEventConsumer consumer =
                new FrequencySuccessReservationPaymentEventConsumer(service);

        consumer.onMessage(payload("SUCCESS"));
        consumer.onMessage(payload("FAILED"));
        consumer.onMessage(payload("PENDING"));

        verify(service).confirm("M001", "TX001");
        verify(service).release("M001", "TX001");
        verify(service, never()).confirm("M001", "TX-PENDING");
        verifyNoMoreInteractions(service);
    }

    /** 畸形支付事件必须抛出以触发 RocketMQ 重试，不能静默确认。 */
    @Test
    void shouldRejectMalformedPaymentEvent() {
        FrequencySuccessReservationService service = mock(FrequencySuccessReservationService.class);
        FrequencySuccessReservationPaymentEventConsumer consumer =
                new FrequencySuccessReservationPaymentEventConsumer(service);

        assertThatThrownBy(() -> consumer.onMessage("{invalid-json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("risk payment event payload is invalid");

        verifyNoMoreInteractions(service);
    }

    private String payload(String status) {
        RiskPaymentTransactionEventMessage message = new RiskPaymentTransactionEventMessage();
        message.setMessageId("EV-" + status);
        message.setMerchantId("M001");
        message.setTransactionId("TX001");
        message.setTransactionStatus(status);
        message.setEventType(RiskMqConstants.PAYMENT_TRANSACTION_STATUS_CHANGED_TAG);
        return JsonUtils.toJsonString(message);
    }
}
