package com.scott.payment.settlement.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONWriter;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.ExchangeRateUsageMessage;
import com.scott.payment.finance.settlement.model.SettlementRateModels.CurrencyPair;
import com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import com.scott.payment.finance.settlement.model.SettlementRateModels.QuoteDirection;
import com.scott.payment.finance.settlement.model.SettlementRateModels.RateMatrix;
import com.scott.payment.settlement.dto.SettlementLockedRateMatrix;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementEventOutboxDO;
import com.scott.payment.settlement.mapper.SettlementEventOutboxMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 结算汇率使用 Outbox 测试。
 *
 * <p>覆盖跨币种事件生成、同币种恒等率跳过和唯一键冲突后的冻结事实校验。</p>
 */
class SettlementRateUsageOutboxServiceTest {

    /**
     * 结算成功后只应为 EUR/USD 生成事件，USD/USD 恒等率不得形成外汇使用快照。
     */
    @Test
    void shouldSaveOnlyCrossCurrencyUsageEvent() {
        SettlementEventOutboxMapper mapper = mock(SettlementEventOutboxMapper.class);
        AtomicReference<SettlementEventOutboxDO> stored = new AtomicReference<>();
        when(mapper.insertRateUsageIdempotent(any())).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.selectByEventNo(any())).thenAnswer(invocation -> stored.get());
        SettlementRateUsageOutboxService service = new SettlementRateUsageOutboxService(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 11, 1, 12, 0);

        int count = service.saveForCalculatedBatch(batch(), rates(now), now);

        assertThat(count).isEqualTo(1);
        SettlementEventOutboxDO row = stored.get();
        assertThat(row.getRateUsageId()).isEqualTo(11L);
        assertThat(row.getCandidateId()).isNull();
        assertThat(row.getTopic()).isEqualTo(MqTopic.EXCHANGE_RATE_USAGE);
        assertThat(row.getTag()).isEqualTo(MqTag.EXCHANGE_RATE_USAGE);
        ExchangeRateUsageMessage message = JsonUtils.parseObject(
                row.getPayloadJson(), ExchangeRateUsageMessage.class);
        assertThat(message.getBusinessRateId()).isEqualTo(101L);
        assertThat(message.getBaseCurrency()).isEqualTo("EUR");
        assertThat(message.getQuoteCurrency()).isEqualTo("USD");
        assertThat(message.getUsedRate()).isEqualByComparingTo("1.080000000000");
        verify(mapper).selectByEventNo("FXST:SB20261101-00000001:11");
    }

    /**
     * 唯一事件号命中但冻结 JSON 不一致时必须中断结算事务，禁止静默覆盖审计事实。
     */
    @Test
    void shouldRejectConflictingIdempotentOutboxFact() {
        SettlementEventOutboxMapper mapper = mock(SettlementEventOutboxMapper.class);
        when(mapper.insertRateUsageIdempotent(any())).thenReturn(0);
        SettlementEventOutboxDO existing = new SettlementEventOutboxDO();
        existing.setEventNo("FXST:SB20261101-00000001:11");
        existing.setSettlementBatchNo("SB20261101-00000001");
        existing.setRateUsageId(11L);
        existing.setTopic(MqTopic.EXCHANGE_RATE_USAGE);
        existing.setTag(MqTag.EXCHANGE_RATE_USAGE);
        existing.setMessageKey(existing.getEventNo());
        existing.setPayloadJson("{\"conflict\":true}");
        when(mapper.selectByEventNo(existing.getEventNo())).thenReturn(existing);
        SettlementRateUsageOutboxService service = new SettlementRateUsageOutboxService(mapper);

        assertThatThrownBy(() -> service.saveForCalculatedBatch(
                batch(), rates(LocalDateTime.of(2026, 11, 1, 12, 0)),
                LocalDateTime.of(2026, 11, 1, 12, 0)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different persisted outbox fact");
    }

    /**
     * MySQL JSON 列回读会规范化空白，语义相同的冻结消息不得被误判为幂等冲突。
     */
    @Test
    void shouldAcceptDatabaseNormalizedJsonPayload() {
        SettlementEventOutboxMapper mapper = mock(SettlementEventOutboxMapper.class);
        AtomicReference<SettlementEventOutboxDO> inserted = new AtomicReference<>();
        when(mapper.insertRateUsageIdempotent(any())).thenAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.selectByEventNo(any())).thenAnswer(invocation -> databaseRow(inserted.get()));
        SettlementRateUsageOutboxService service = new SettlementRateUsageOutboxService(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 11, 1, 12, 0);

        int count = service.saveForCalculatedBatch(batch(), rates(now), now);

        assertThat(count).isEqualTo(1);
    }

    /**
     * MySQL 规范化 JSON 数字后，真实汇率发生变化仍必须中断事务，禁止放宽资金事实校验。
     */
    @Test
    void shouldRejectDifferentRateAfterDatabaseNormalization() {
        SettlementEventOutboxMapper mapper = mock(SettlementEventOutboxMapper.class);
        AtomicReference<SettlementEventOutboxDO> inserted = new AtomicReference<>();
        when(mapper.insertRateUsageIdempotent(any())).thenAnswer(invocation -> {
            inserted.set(invocation.getArgument(0));
            return 1;
        });
        when(mapper.selectByEventNo(any())).thenAnswer(invocation ->
                databaseRow(inserted.get(), new BigDecimal("1.081")));
        SettlementRateUsageOutboxService service = new SettlementRateUsageOutboxService(mapper);
        LocalDateTime now = LocalDateTime.of(2026, 11, 1, 12, 0);

        assertThatThrownBy(() -> service.saveForCalculatedBatch(batch(), rates(now), now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("different persisted outbox fact");
    }

    /**
     * 创建测试结算批次身份。
     */
    private SettlementBatchDO batch() {
        SettlementBatchDO batch = new SettlementBatchDO();
        batch.setSettlementBatchNo("SB20261101-00000001");
        batch.setTargetCurrency("USD");
        return batch;
    }

    /**
     * 创建一个跨币种业务汇率和一个同币种恒等率的完整锁定矩阵。
     */
    private SettlementLockedRateMatrix rates(LocalDateTime now) {
        LockedRate eur = new LockedRate(new CurrencyPair("EUR", "USD"),
                new BigDecimal("1.080000000000"), 2, 2, "BOC", "101",
                QuoteDirection.DIRECT, now.minusMinutes(1));
        LockedRate usd = new LockedRate(new CurrencyPair("USD", "USD"),
                new BigDecimal("1.000000000000"), 2, 2, "SYSTEM_IDENTITY", null,
                QuoteDirection.DIRECT, now.minusMinutes(1));
        return new SettlementLockedRateMatrix(
                RateMatrix.of(List.of(eur, usd)), Map.of("EUR", 11L, "USD", 12L));
    }

    /**
     * 模拟 MySQL JSON 列对尾随零和字段空白的规范化，同时保持其余冻结身份不变。
     */
    private SettlementEventOutboxDO databaseRow(SettlementEventOutboxDO source) {
        return databaseRow(source, new BigDecimal("1.08"));
    }

    /**
     * 模拟 MySQL JSON 列对 BigDecimal 尾随零和字段空白的规范化。
     */
    private SettlementEventOutboxDO databaseRow(SettlementEventOutboxDO source,
                                                 BigDecimal storedRate) {
        SettlementEventOutboxDO row = new SettlementEventOutboxDO();
        row.setEventNo(source.getEventNo());
        row.setSettlementBatchNo(source.getSettlementBatchNo());
        row.setCandidateId(source.getCandidateId());
        row.setRateUsageId(source.getRateUsageId());
        row.setTopic(source.getTopic());
        row.setTag(source.getTag());
        row.setMessageKey(source.getMessageKey());
        JSONObject payload = JSON.parseObject(source.getPayloadJson());
        payload.put("usedRate", storedRate);
        row.setPayloadJson(JSON.toJSONString(payload,
                JSONWriter.Feature.PrettyFormat));
        return row;
    }
}
