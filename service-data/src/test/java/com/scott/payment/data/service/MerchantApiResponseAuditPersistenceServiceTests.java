package com.scott.payment.data.service;

import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.mq.message.MerchantApiResponseAuditMessage;
import com.scott.payment.data.entity.DataMerchantApiResponseAuditDO;
import com.scott.payment.data.mapper.DataMerchantApiResponseAuditMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商户响应审计数据库最终幂等测试。
 *
 * <p>覆盖首次更新、重复投递、冲突关闭、基础记录延迟以及异步边界二次脱敏。</p>
 */
class MerchantApiResponseAuditPersistenceServiceTests {

    /**
     * 首次消息应二次脱敏敏感字段并完成一行条件更新。
     */
    @Test
    void shouldMaskAgainAndUpdateFirstResponseAudit() {
        DataMerchantApiResponseAuditMapper mapper = mock(DataMerchantApiResponseAuditMapper.class);
        when(mapper.updateFirstResponseAudit(any())).thenReturn(1);
        MerchantApiResponseAuditPersistenceService service =
                new MerchantApiResponseAuditPersistenceService(mapper);

        assertThat(service.persist(message()))
                .isEqualTo(MerchantApiResponseAuditPersistenceService.PersistenceResult.UPDATED);

        ArgumentCaptor<DataMerchantApiResponseAuditDO> captor =
                ArgumentCaptor.forClass(DataMerchantApiResponseAuditDO.class);
        verify(mapper).updateFirstResponseAudit(captor.capture());
        assertThat(captor.getValue().getResponsePlainJsonMasked())
                .doesNotContain("4111111111111111", "\"cvv\":\"123\"")
                .contains("411111******1111", "\"cvv\":\"***\"");
    }

    /**
     * 条件更新未命中但数据库事实完全一致时，应按 MQ 重复投递正常确认。
     */
    @Test
    void shouldAcceptIdenticalDuplicate() {
        DataMerchantApiResponseAuditMapper mapper = mock(DataMerchantApiResponseAuditMapper.class);
        when(mapper.updateFirstResponseAudit(any())).thenReturn(0);
        when(mapper.selectResponseFacts(any())).thenReturn(List.of(fact(message())));
        MerchantApiResponseAuditPersistenceService service =
                new MerchantApiResponseAuditPersistenceService(mapper);

        assertThat(service.persist(message()))
                .isEqualTo(MerchantApiResponseAuditPersistenceService.PersistenceResult.DUPLICATE);
    }

    /**
     * 基础交互日志尚未提交时必须抛出异常，由 RocketMQ 稍后重试。
     */
    @Test
    void shouldRetryWhenInteractionLogIsNotReady() {
        DataMerchantApiResponseAuditMapper mapper = mock(DataMerchantApiResponseAuditMapper.class);
        when(mapper.updateFirstResponseAudit(any())).thenReturn(0);
        when(mapper.selectResponseFacts(any())).thenReturn(List.of());
        MerchantApiResponseAuditPersistenceService service =
                new MerchantApiResponseAuditPersistenceService(mapper);

        assertThatThrownBy(() -> service.persist(message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not ready");
    }

    /**
     * 同一响应身份已经保存不同密文摘要时必须失败关闭，禁止覆盖首次响应。
     */
    @Test
    void shouldRejectConflictingFirstResponseFact() {
        DataMerchantApiResponseAuditMapper mapper = mock(DataMerchantApiResponseAuditMapper.class);
        when(mapper.updateFirstResponseAudit(any())).thenReturn(0);
        DataMerchantApiResponseAuditDO conflict = fact(message());
        conflict.setResponseCipherDigest("b".repeat(64));
        when(mapper.selectResponseFacts(any())).thenReturn(List.of(conflict));
        MerchantApiResponseAuditPersistenceService service =
                new MerchantApiResponseAuditPersistenceService(mapper);

        assertThatThrownBy(() -> service.persist(message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("conflicts");
    }

    /**
     * 缺少请求号的兼容消息命中多条交互事实时必须回滚，不得任意选一行。
     */
    @Test
    void shouldRejectNonUniqueCompatibilityIdentity() {
        DataMerchantApiResponseAuditMapper mapper = mock(DataMerchantApiResponseAuditMapper.class);
        when(mapper.updateFirstResponseAudit(any())).thenReturn(0);
        DataMerchantApiResponseAuditDO fact = fact(message());
        when(mapper.selectResponseFacts(any())).thenReturn(List.of(fact, fact));
        MerchantApiResponseAuditPersistenceService service =
                new MerchantApiResponseAuditPersistenceService(mapper);

        assertThatThrownBy(() -> service.persist(message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("not unique");
    }

    /**
     * 创建合法商户响应审计消息。
     */
    private MerchantApiResponseAuditMessage message() {
        MerchantApiResponseAuditMessage message = new MerchantApiResponseAuditMessage();
        message.setTransactionId("TX202611030001");
        message.setTransactionDateTime(LocalDateTime.of(2026, 11, 3, 10, 30, 0, 123_000_000));
        message.setRequestId("ORDER-1001");
        message.setResponsePlainJsonMasked(
                "{\"cardNo\":\"4111111111111111\",\"cvv\":\"123\",\"result\":\"SUCCESS\"}");
        message.setResponseCipherDigest("a".repeat(64));
        message.setResponseCipherMasked("12345678***87654321");
        message.setResponseTime(LocalDateTime.of(2026, 11, 3, 10, 30, 1, 987_000_000));
        return message;
    }

    /**
     * 创建与消息完全一致的数据库响应事实。
     */
    private DataMerchantApiResponseAuditDO fact(MerchantApiResponseAuditMessage message) {
        DataMerchantApiResponseAuditDO fact = new DataMerchantApiResponseAuditDO();
        fact.setTransactionId(message.getTransactionId());
        fact.setTransactionDateTime(message.getTransactionDateTime());
        fact.setRequestId(message.getRequestId());
        fact.setResponsePlainJsonMasked(SensitiveDataMaskUtils.maskTransactionInteractionJsonSafely(
                message.getResponsePlainJsonMasked()));
        fact.setResponseCipherDigest(message.getResponseCipherDigest());
        fact.setResponseCipherMasked(message.getResponseCipherMasked());
        fact.setResponseTime(message.getResponseTime());
        return fact;
    }
}
