package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.message.MerchantApiResponseAuditMessage;
import com.scott.payment.data.service.MerchantApiResponseAuditPersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商户响应审计消息契约测试。
 *
 * <p>覆盖合法消息、来源伪造、稳定消息号不一致、错误时间精度和畸形载荷日志脱敏。</p>
 */
@ExtendWith(OutputCaptureExtension.class)
class MerchantApiResponseAuditConsumerTests {

    /**
     * 合法的 OpenAPI 响应审计消息应进入数据库最终幂等服务。
     */
    @Test
    void shouldPersistValidResponseAudit() {
        MerchantApiResponseAuditPersistenceService persistenceService =
                mock(MerchantApiResponseAuditPersistenceService.class);
        when(persistenceService.persist(any()))
                .thenReturn(MerchantApiResponseAuditPersistenceService.PersistenceResult.UPDATED);
        MerchantApiResponseAuditConsumer consumer =
                new MerchantApiResponseAuditConsumer(persistenceService);
        MerchantApiResponseAuditMessage message = message();

        consumer.onMessage(JsonUtils.toJsonString(message));

        verify(persistenceService).persist(any(MerchantApiResponseAuditMessage.class));
    }

    /**
     * 非 OpenAPI 生产服务不得向交易分片表写入响应审计。
     */
    @Test
    void shouldRejectUnexpectedProducer() {
        MerchantApiResponseAuditPersistenceService persistenceService =
                mock(MerchantApiResponseAuditPersistenceService.class);
        MerchantApiResponseAuditConsumer consumer =
                new MerchantApiResponseAuditConsumer(persistenceService);
        MerchantApiResponseAuditMessage message = message();
        message.setProducerService("service-payment");

        assertThatThrownBy(() -> consumer.onMessage(JsonUtils.toJsonString(message)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("producer service");
        verify(persistenceService, never()).persist(any());
    }

    /**
     * 伪造稳定消息号必须在数据库访问前被拒绝。
     */
    @Test
    void shouldRejectMismatchedStableMessageId() {
        MerchantApiResponseAuditPersistenceService persistenceService =
                mock(MerchantApiResponseAuditPersistenceService.class);
        MerchantApiResponseAuditConsumer consumer =
                new MerchantApiResponseAuditConsumer(persistenceService);
        MerchantApiResponseAuditMessage message = message();
        message.setMessageId("f".repeat(64));

        assertThatThrownBy(() -> consumer.onMessage(JsonUtils.toJsonString(message)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stable audit identity");
        verify(persistenceService, never()).persist(any());
    }

    /**
     * 分片时间超过 DATETIME(3) 精度时必须拒绝，防止路由到错误物理表或更新未命中。
     */
    @Test
    void shouldRejectNanosecondShardTime() {
        MerchantApiResponseAuditPersistenceService persistenceService =
                mock(MerchantApiResponseAuditPersistenceService.class);
        MerchantApiResponseAuditConsumer consumer =
                new MerchantApiResponseAuditConsumer(persistenceService);
        MerchantApiResponseAuditMessage message = message();
        message.setTransactionDateTime(message.getTransactionDateTime().withNano(123_456_789));
        message.setMessageId(MerchantApiResponseAuditMessage.stableMessageId(
                message.getTransactionId(), message.getTransactionDateTime(), message.getRequestId()));

        assertThatThrownBy(() -> consumer.onMessage(JsonUtils.toJsonString(message)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("millisecond precision");
        verify(persistenceService, never()).persist(any());
    }

    /**
     * 畸形消息的异常和日志只能记录长度，不能泄露载荷中的卡号。
     */
    @Test
    void shouldNotLogMalformedPayload(CapturedOutput output) {
        MerchantApiResponseAuditConsumer consumer = new MerchantApiResponseAuditConsumer(
                mock(MerchantApiResponseAuditPersistenceService.class));
        String malformed = "{\"responsePlainJsonMasked\":\"4111111111111111\"";

        assertThatThrownBy(() -> consumer.onMessage(malformed))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("4111111111111111");
        assertThat(output.getOut()).doesNotContain("4111111111111111");
    }

    /**
     * 创建满足固定事件、来源、摘要和毫秒时间精度的消息。
     */
    private MerchantApiResponseAuditMessage message() {
        MerchantApiResponseAuditMessage message = new MerchantApiResponseAuditMessage();
        message.setEventType(MqTag.MERCHANT_API_RESPONSE_AUDIT);
        message.setProducerService("service-openapi");
        message.setTransactionId("TX202611030001");
        message.setTransactionDateTime(LocalDateTime.of(2026, 11, 3, 10, 30, 0, 123_000_000));
        message.setRequestId("ORDER-1001");
        message.setResponsePlainJsonMasked("{\"result\":\"SUCCESS\"}");
        message.setResponseCipherDigest("a".repeat(64));
        message.setResponseCipherMasked("12345678***87654321");
        message.setResponseTime(LocalDateTime.of(2026, 11, 3, 10, 30, 1, 987_000_000));
        message.setCreatedAt(LocalDateTime.of(2026, 11, 3, 10, 30, 2));
        message.setTraceId("trace-1001");
        message.setMessageId(MerchantApiResponseAuditMessage.stableMessageId(
                message.getTransactionId(), message.getTransactionDateTime(), message.getRequestId()));
        return message;
    }
}
