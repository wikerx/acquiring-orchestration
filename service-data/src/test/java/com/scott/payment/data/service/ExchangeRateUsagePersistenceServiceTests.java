package com.scott.payment.data.service;

import com.scott.payment.component.mq.message.ExchangeRateUsageMessage;
import com.scott.payment.data.entity.DataExchangeRateUsageSnapshotDO;
import com.scott.payment.data.mapper.DataExchangeRateUsageSnapshotMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 汇率使用快照最终幂等测试。
 *
 * <p>覆盖首次写入、完全一致的重复消息以及事件号复用造成的事实冲突。</p>
 */
class ExchangeRateUsagePersistenceServiceTests {

    /**
     * 首次消息应保留上游业务时间、汇率来源和业务汇率主键并写入一行。
     */
    @Test
    void shouldPersistCompleteUsageFact() {
        DataExchangeRateUsageSnapshotMapper mapper = mock(DataExchangeRateUsageSnapshotMapper.class);
        when(mapper.insert(any(DataExchangeRateUsageSnapshotDO.class))).thenReturn(1);
        ExchangeRateUsagePersistenceService service = new ExchangeRateUsagePersistenceService(mapper);

        assertThat(service.persist(message())).isTrue();

        ArgumentCaptor<DataExchangeRateUsageSnapshotDO> captor =
                ArgumentCaptor.forClass(DataExchangeRateUsageSnapshotDO.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue())
                .extracting(DataExchangeRateUsageSnapshotDO::getUsageEventId,
                        DataExchangeRateUsageSnapshotDO::getBusinessNo,
                        DataExchangeRateUsageSnapshotDO::getBusinessRateId,
                        DataExchangeRateUsageSnapshotDO::getAppliedTime)
                .containsExactly("FXTR:TX202611010001", "TX202611010001", 101L,
                        LocalDateTime.of(2026, 11, 1, 12, 1));
    }

    /**
     * 数据库唯一键冲突且全部不可变事实一致时，应按 MQ 重复投递正常确认。
     */
    @Test
    void shouldAcceptIdenticalDatabaseDuplicate() {
        DataExchangeRateUsageSnapshotMapper mapper = mock(DataExchangeRateUsageSnapshotMapper.class);
        doThrow(new DuplicateKeyException("duplicate usage_event_id"))
                .when(mapper).insert(any(DataExchangeRateUsageSnapshotDO.class));
        when(mapper.selectByUsageEventId("FXTR:TX202611010001")).thenReturn(entity(message()));
        ExchangeRateUsagePersistenceService service = new ExchangeRateUsagePersistenceService(mapper);

        assertThat(service.persist(message())).isFalse();
    }

    /**
     * 消息携带纳秒时，数据库 DATETIME(3) 回读值与毫秒规范化后的事实仍应按重复成功处理。
     */
    @Test
    void shouldAcceptDuplicateAfterDatabaseMillisecondTruncation() {
        DataExchangeRateUsageSnapshotMapper mapper = mock(DataExchangeRateUsageSnapshotMapper.class);
        doThrow(new DuplicateKeyException("duplicate usage_event_id"))
                .when(mapper).insert(any(DataExchangeRateUsageSnapshotDO.class));
        ExchangeRateUsageMessage message = message();
        message.setRateEffectiveTime(message.getRateEffectiveTime().withNano(123_456_789));
        message.setAppliedTime(message.getAppliedTime().withNano(987_654_321));
        DataExchangeRateUsageSnapshotDO existing = entity(message);
        existing.setRateEffectiveTime(message.getRateEffectiveTime().withNano(123_000_000));
        existing.setAppliedTime(message.getAppliedTime().withNano(987_000_000));
        when(mapper.selectByUsageEventId("FXTR:TX202611010001")).thenReturn(existing);
        ExchangeRateUsagePersistenceService service = new ExchangeRateUsagePersistenceService(mapper);

        assertThat(service.persist(message)).isFalse();
    }

    /**
     * 同一事件号对应不同业务汇率时必须失败关闭，不能把冲突事实误认为幂等成功。
     */
    @Test
    void shouldRejectConflictingDatabaseDuplicate() {
        DataExchangeRateUsageSnapshotMapper mapper = mock(DataExchangeRateUsageSnapshotMapper.class);
        doThrow(new DuplicateKeyException("duplicate usage_event_id"))
                .when(mapper).insert(any(DataExchangeRateUsageSnapshotDO.class));
        DataExchangeRateUsageSnapshotDO existing = entity(message());
        existing.setUsedRate(new BigDecimal("1.090000000000"));
        when(mapper.selectByUsageEventId("FXTR:TX202611010001")).thenReturn(existing);
        ExchangeRateUsagePersistenceService service = new ExchangeRateUsagePersistenceService(mapper);

        assertThatThrownBy(() -> service.persist(message()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different persisted fact");
    }

    /**
     * 创建包含完整来源和业务时间的支付汇率使用消息。
     */
    private ExchangeRateUsageMessage message() {
        ExchangeRateUsageMessage message = new ExchangeRateUsageMessage();
        message.setMessageId("FXTR:TX202611010001");
        message.setUsageEventId("FXTR:TX202611010001");
        message.setProducerService("service-payment");
        message.setRateType("TRANSACTION_RATE");
        message.setUsageScene("TRANSACTION");
        message.setBusinessType("PAYMENT");
        message.setBusinessNo("TX202611010001");
        message.setBaseCurrency("EUR");
        message.setQuoteCurrency("USD");
        message.setUsedRate(new BigDecimal("1.080000000000"));
        message.setBusinessRateId(101L);
        message.setRawRateId(81L);
        message.setRuleId(91L);
        message.setRateSource("BOC");
        message.setSourceQuoteDirection("DIRECT");
        message.setRateEffectiveTime(LocalDateTime.of(2026, 11, 1, 12, 0));
        message.setCalculationDescription("labelAmount * transactionRate");
        message.setAppliedTime(LocalDateTime.of(2026, 11, 1, 12, 1));
        message.setCreatedAt(LocalDateTime.of(2026, 11, 1, 12, 1));
        return message;
    }

    /**
     * 把测试消息映射为数据库已提交事实，模拟唯一键冲突后的回读结果。
     */
    private DataExchangeRateUsageSnapshotDO entity(ExchangeRateUsageMessage message) {
        DataExchangeRateUsageSnapshotDO entity = new DataExchangeRateUsageSnapshotDO();
        entity.setUsageEventId(message.getUsageEventId());
        entity.setProducerService(message.getProducerService());
        entity.setRateType(message.getRateType());
        entity.setUsageScene(message.getUsageScene());
        entity.setBusinessType(message.getBusinessType());
        entity.setBusinessNo(message.getBusinessNo());
        entity.setBaseCurrency(message.getBaseCurrency());
        entity.setQuoteCurrency(message.getQuoteCurrency());
        entity.setUsedRate(message.getUsedRate());
        entity.setBusinessRateId(message.getBusinessRateId());
        entity.setRawRateId(message.getRawRateId());
        entity.setRuleId(message.getRuleId());
        entity.setRateSource(message.getRateSource());
        entity.setSourceQuoteDirection(message.getSourceQuoteDirection());
        entity.setRateEffectiveTime(message.getRateEffectiveTime());
        entity.setCalculationDescription(message.getCalculationDescription());
        entity.setAppliedTime(message.getAppliedTime());
        return entity;
    }
}
