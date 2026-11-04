package com.scott.payment.openapi.support;

import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.MerchantApiResponseAuditMessage;
import com.scott.payment.component.mq.publisher.IndependentReliableMqPublisher;
import com.scott.payment.openapi.client.payment.PaymentInternalClient;
import com.scott.payment.openapi.client.payment.dto.TransactionMerchantApiResponseLogUpdateClientRequestDTO;
import com.scott.payment.openapi.config.MerchantApiResponseAuditProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商户响应审计发布器行为测试。
 *
 * <p>覆盖 MQ 主路径、HTTP 回滚路径、稳定消息身份以及审计基础设施失败不影响商户响应。</p>
 */
class MerchantApiResponseAuditPublisherTests {

    /**
     * MQ 模式应写入固定 Topic/Tag，并保留真实交易分片时间和受控审计字段。
     */
    @Test
    void shouldPublishAuditThroughReliableMq() {
        PaymentInternalClient paymentClient = mock(PaymentInternalClient.class);
        IndependentReliableMqPublisher mqPublisher = mock(IndependentReliableMqPublisher.class);
        when(mqPublisher.publish(any(), any(), any())).thenReturn("event-id");
        MerchantApiResponseAuditPublisher publisher = publisher(
                paymentClient, mqPublisher, MerchantApiResponseAuditProperties.Mode.MQ);

        publisher.publish(request());

        ArgumentCaptor<MerchantApiResponseAuditMessage> captor =
                ArgumentCaptor.forClass(MerchantApiResponseAuditMessage.class);
        verify(mqPublisher).publish(
                org.mockito.ArgumentMatchers.eq(MqTopic.PAYMENT_EVENT),
                org.mockito.ArgumentMatchers.eq(MqTag.MERCHANT_API_RESPONSE_AUDIT),
                captor.capture());
        assertThat(captor.getValue())
                .extracting(MerchantApiResponseAuditMessage::getProducerService,
                        MerchantApiResponseAuditMessage::getTransactionId,
                        MerchantApiResponseAuditMessage::getRequestId,
                        MerchantApiResponseAuditMessage::getTransactionDateTime,
                        MerchantApiResponseAuditMessage::getResponseTime)
                .containsExactly("service-openapi", "TX202611030001", "ORDER-1001",
                        LocalDateTime.of(2026, 11, 3, 10, 30, 0, 123_000_000),
                        LocalDateTime.of(2026, 11, 3, 10, 30, 1, 987_000_000));
        assertThat(captor.getValue().getMessageId()).matches("[0-9a-f]{64}");
        verify(paymentClient, never()).updateMerchantApiResponseLog(any());
    }

    /**
     * 同一交易、分片和商户请求重复发布时应生成相同消息号，由 Outbox 检查事实冲突。
     */
    @Test
    void shouldGenerateStableMessageIdForSameAuditIdentity() {
        IndependentReliableMqPublisher mqPublisher = mock(IndependentReliableMqPublisher.class);
        when(mqPublisher.publish(any(), any(), any())).thenReturn("event-id");
        MerchantApiResponseAuditPublisher publisher = publisher(
                mock(PaymentInternalClient.class), mqPublisher,
                MerchantApiResponseAuditProperties.Mode.MQ);

        publisher.publish(request());
        TransactionMerchantApiResponseLogUpdateClientRequestDTO retry = request();
        retry.setResponseCipherDigest("b".repeat(64));
        retry.setResponseTime(retry.getResponseTime().plusSeconds(1));
        publisher.publish(retry);

        ArgumentCaptor<MerchantApiResponseAuditMessage> captor =
                ArgumentCaptor.forClass(MerchantApiResponseAuditMessage.class);
        verify(mqPublisher, times(2)).publish(any(), any(), captor.capture());
        assertThat(captor.getAllValues())
                .extracting(MerchantApiResponseAuditMessage::getMessageId)
                .containsExactly(captor.getAllValues().get(0).getMessageId(),
                        captor.getAllValues().get(0).getMessageId());
    }

    /**
     * HTTP 模式必须保留原内部回写路径，作为 MQ 灰度期间的快速回滚手段。
     */
    @Test
    void shouldUseHttpCompatibilityMode() {
        PaymentInternalClient paymentClient = mock(PaymentInternalClient.class);
        when(paymentClient.updateMerchantApiResponseLog(any())).thenReturn(true);
        IndependentReliableMqPublisher mqPublisher = mock(IndependentReliableMqPublisher.class);
        MerchantApiResponseAuditPublisher publisher = publisher(
                paymentClient, mqPublisher, MerchantApiResponseAuditProperties.Mode.HTTP);
        TransactionMerchantApiResponseLogUpdateClientRequestDTO request = request();

        publisher.publish(request);

        verify(paymentClient).updateMerchantApiResponseLog(request);
        verify(mqPublisher, never()).publish(any(), any(), any());
    }

    /**
     * Outbox 写入异常只允许形成受控告警，不能阻断已经生成的商户响应。
     */
    @Test
    void shouldNotPropagateReliableOutboxFailure() {
        IndependentReliableMqPublisher mqPublisher = mock(IndependentReliableMqPublisher.class);
        when(mqPublisher.publish(any(), any(), any()))
                .thenThrow(new IllegalStateException("outbox unavailable"));
        MerchantApiResponseAuditPublisher publisher = publisher(
                mock(PaymentInternalClient.class), mqPublisher,
                MerchantApiResponseAuditProperties.Mode.MQ);

        assertThatCode(() -> publisher.publish(request())).doesNotThrowAnyException();
    }

    /**
     * 创建指定投递模式的发布器。
     */
    private MerchantApiResponseAuditPublisher publisher(
            PaymentInternalClient paymentClient,
            IndependentReliableMqPublisher mqPublisher,
            MerchantApiResponseAuditProperties.Mode mode) {
        MerchantApiResponseAuditProperties properties = new MerchantApiResponseAuditProperties();
        properties.setMode(mode);
        return new MerchantApiResponseAuditPublisher(paymentClient, mqPublisher, properties);
    }

    /**
     * 创建包含纳秒输入的合法响应审计请求，用于验证数据库毫秒精度收敛。
     */
    private TransactionMerchantApiResponseLogUpdateClientRequestDTO request() {
        TransactionMerchantApiResponseLogUpdateClientRequestDTO request =
                new TransactionMerchantApiResponseLogUpdateClientRequestDTO();
        request.setTransactionId("TX202611030001");
        request.setTransactionDateTime(LocalDateTime.of(
                2026, 11, 3, 10, 30, 0, 123_456_789));
        request.setRequestId("ORDER-1001");
        request.setResponsePlainJsonMasked("{\"result\":\"SUCCESS\"}");
        request.setResponseCipherDigest("a".repeat(64));
        request.setResponseCipherMasked("12345678***87654321");
        request.setResponseTime(LocalDateTime.of(
                2026, 11, 3, 10, 30, 1, 987_654_321));
        return request;
    }
}
