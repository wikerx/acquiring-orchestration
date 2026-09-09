package com.scott.payment.settlement.service.impl;

import com.scott.payment.finance.settlement.model.SettlementRateModels.CurrencyPair;
import com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import com.scott.payment.finance.settlement.model.SettlementRateModels.QuoteDirection;
import com.scott.payment.finance.settlement.model.SettlementRateModels.RateMatrix;
import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.dto.SettlementCurrency;
import com.scott.payment.settlement.dto.SettlementLockedRateMatrix;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementBatchRateDO;
import com.scott.payment.settlement.exception.SettlementProcessingException;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementBatchRateMapper;
import com.scott.payment.settlement.service.SettlementRateResolutionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementBatchRateLockServiceTest
 * @date : 2026-08-26 23:55
 * @email : scott_x@163.com
 * @description : 验证批次汇率完整追加回读、状态 CAS，以及已有部分矩阵绝不重新解析或补写。
 * @status : create
 */
class DefaultSettlementBatchRateLockServiceTest {

    private SettlementBatchMapper batchMapper;
    private SettlementBatchRateMapper rateMapper;
    private SettlementRateResolutionService resolutionService;
    private DefaultSettlementBatchRateLockService service;

    @BeforeEach
    void setUp() {
        batchMapper = mock(SettlementBatchMapper.class);
        rateMapper = mock(SettlementBatchRateMapper.class);
        resolutionService = mock(SettlementRateResolutionService.class);
        service = new DefaultSettlementBatchRateLockService(batchMapper, rateMapper, resolutionService);
    }

    /** 空矩阵必须一次解析并完整回读，随后才能进入 RATE_LOCKED。 */
    @Test
    void shouldInsertReadBackAndLockCompleteMatrix() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 10, 0);
        SettlementBatchDO batch = batch(now);
        SettlementBatchFacts facts = facts();
        RateMatrix resolved = matrix(now);
        AtomicReference<List<SettlementBatchRateDO>> inserted = new AtomicReference<>();
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(rateMapper.selectByBatchNo(batch.getSettlementBatchNo())).thenAnswer(invocation -> {
            List<SettlementBatchRateDO> rows = inserted.get();
            return rows == null ? List.of() : rows;
        });
        when(resolutionService.resolve(facts.currencies(), "GBP", 2, now)).thenReturn(resolved);
        when(rateMapper.insertBatchIdempotent(anyList())).thenAnswer(invocation -> {
            List<SettlementBatchRateDO> rows = invocation.getArgument(0);
            long id = 10L;
            for (SettlementBatchRateDO row : rows) {
                row.setId(id++);
            }
            inserted.set(List.copyOf(rows));
            return rows.size();
        });
        when(batchMapper.markRateLocked(batch.getSettlementBatchNo(), "worker-1", 4L, now))
                .thenReturn(1);

        SettlementLockedRateMatrix result = service.lockOrLoad(
                batch, facts, "worker-1", now);

        assertThat(result.matrix().rates()).hasSize(2);
        assertThat(result.rateIdsBySourceCurrency()).containsOnlyKeys("EUR", "GBP");
        assertThat(batch.getBatchStatus()).isEqualTo("RATE_LOCKED");
        verify(batchMapper).markRateLocked(batch.getSettlementBatchNo(), "worker-1", 4L, now);
    }

    /** 已存在任意但不完整的矩阵必须进入人工复核语义，禁止使用新的估值时间补齐。 */
    @Test
    void shouldRejectPartialStoredMatrixWithoutResolvingAgain() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 10, 0);
        SettlementBatchDO batch = batch(now);
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(rateMapper.selectByBatchNo(batch.getSettlementBatchNo()))
                .thenReturn(List.of(rateRow(10L, identity(now))));

        assertThatThrownBy(() -> service.lockOrLoad(batch, facts(), "worker-1", now))
                .isInstanceOf(SettlementProcessingException.class)
                .hasMessageContaining("incomplete");
        verify(resolutionService, never()).resolve(
                org.mockito.ArgumentMatchers.anySet(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any());
    }

    private SettlementBatchDO batch(LocalDateTime now) {
        SettlementBatchDO row = new SettlementBatchDO();
        row.setSettlementBatchNo("SB20260826-00000001");
        row.setTargetCurrency("GBP");
        row.setTargetCurrencyExponent(2);
        row.setBatchStatus("CLAIMED");
        row.setProcessingOwner("worker-1");
        row.setProcessingDeadline(now.plusMinutes(5));
        row.setVersion(4L);
        return row;
    }

    private SettlementBatchFacts facts() {
        return new SettlementBatchFacts(List.of(), List.of(), List.of(), Set.of(
                new SettlementCurrency("EUR", 2), new SettlementCurrency("GBP", 2)));
    }

    private RateMatrix matrix(LocalDateTime now) {
        return RateMatrix.of(List.of(
                new LockedRate(new CurrencyPair("EUR", "GBP"), new BigDecimal("0.800000000000"),
                        2, 2, "ECB", "100", QuoteDirection.DIRECT, now.minusMinutes(1)),
                identity(now)));
    }

    private LockedRate identity(LocalDateTime now) {
        return new LockedRate(new CurrencyPair("GBP", "GBP"), new BigDecimal("1.000000000000"),
                2, 2, "SYSTEM_IDENTITY", null, QuoteDirection.DIRECT, now);
    }

    private SettlementBatchRateDO rateRow(long id, LockedRate rate) {
        SettlementBatchRateDO row = new SettlementBatchRateDO();
        row.setId(id);
        row.setSettlementBatchNo("SB20260826-00000001");
        row.setSourceCurrency(rate.pair().sourceCurrency());
        row.setTargetCurrency(rate.pair().targetCurrency());
        row.setRateType("SETTLEMENT");
        row.setDirectRate(rate.directRate());
        row.setSourceCurrencyExponent(rate.sourceCurrencyExponent());
        row.setTargetCurrencyExponent(rate.targetCurrencyExponent());
        row.setRateSource(rate.rateSource());
        row.setQuoteId(rate.quoteId());
        row.setSourceQuoteDirection(rate.sourceQuoteDirection().name());
        row.setEffectiveTime(rate.effectiveTime());
        row.setRateStatus("LOCKED");
        return row;
    }
}
