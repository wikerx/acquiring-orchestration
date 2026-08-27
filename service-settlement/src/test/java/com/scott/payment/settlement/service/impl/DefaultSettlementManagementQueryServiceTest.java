package com.scott.payment.settlement.service.impl;

import com.scott.payment.settlement.api.internal.dto.SettlementManagementDTOs.BatchSearchRequest;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementOperationalStateDO;
import com.scott.payment.settlement.mapper.SettlementBatchRateMapper;
import com.scott.payment.settlement.mapper.SettlementManagementMapper;
import com.scott.payment.settlement.mapper.SettlementResultMapper;
import com.scott.payment.settlement.support.SettlementBatchNumberFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 验证结算管理查询的扫描边界、主键游标和有界详情输出。 */
class DefaultSettlementManagementQueryServiceTest {

    private SettlementManagementMapper managementMapper;
    private SettlementBatchRateMapper rateMapper;
    private SettlementResultMapper resultMapper;
    private DefaultSettlementManagementQueryService service;

    @BeforeEach
    void setUp() {
        managementMapper = mock(SettlementManagementMapper.class);
        rateMapper = mock(SettlementBatchRateMapper.class);
        resultMapper = mock(SettlementResultMapper.class);
        service = new DefaultSettlementManagementQueryService(
                managementMapper, rateMapper, resultMapper, new SettlementBatchNumberFormatter());
    }

    @Test
    void searchShouldUseLimitPlusOneAndReturnStableCursor() {
        BatchSearchRequest request = request();
        request.setLimit(2);
        request.setBatchType(" regular ");
        request.setBatchStatus(" posted ");
        when(managementMapper.selectBatches(isNull(), isNull(), anyString(), anyString(),
                any(LocalDate.class), any(LocalDate.class), isNull(), anyInt()))
                .thenReturn(List.of(batch(30L, 3), batch(20L, 2), batch(10L, 1)));

        var response = service.search(request);

        assertThat(response.getRecords()).hasSize(2);
        assertThat(response.isHasMore()).isTrue();
        assertThat(response.getNextCursorId()).isEqualTo(20L);
        assertThat(response.getRecords().get(0).getDisplayBatchNo())
                .isEqualTo("2026-08-26 00000003");
        verify(managementMapper).selectBatches(null, null, "REGULAR", "POSTED",
                request.getBeginBusinessDate(), request.getEndBusinessDate(), null, 3);
    }

    @Test
    void searchShouldRejectMoreThanNinetyThreeInclusiveDaysBeforeDatabaseAccess() {
        BatchSearchRequest request = request();
        request.setEndBusinessDate(request.getBeginBusinessDate().plusDays(93));

        assertThatThrownBy(() -> service.search(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("93 days");
        verify(managementMapper, never()).selectBatches(
                any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void detailShouldReturnEmptyCollectionsAndZeroOperationalCountsWhenNoAsyncRowsExist() {
        SettlementBatchDO batch = batch(30L, 3);
        when(managementMapper.selectBatch(batch.getSettlementBatchNo())).thenReturn(batch);
        when(rateMapper.selectByBatchNo(batch.getSettlementBatchNo())).thenReturn(List.of());
        when(resultMapper.selectSummariesByBatch(batch.getSettlementBatchNo())).thenReturn(List.of());
        when(resultMapper.selectNetPosting(batch.getSettlementBatchNo())).thenReturn(null);
        when(managementMapper.selectOperationalState(batch.getSettlementBatchNo())).thenReturn(null);

        var response = service.detail(batch.getSettlementBatchNo());

        assertThat(response.getBatch().getSettlementBatchNo()).isEqualTo(batch.getSettlementBatchNo());
        assertThat(response.getRates()).isEmpty();
        assertThat(response.getResultSummaries()).isEmpty();
        assertThat(response.getNetPosting()).isNull();
        assertThat(response.getOperationalState().getProjectionTaskCount()).isZero();
        assertThat(response.getOperationalState().getOutboxEventCount()).isZero();
    }

    private BatchSearchRequest request() {
        BatchSearchRequest request = new BatchSearchRequest();
        request.setBeginBusinessDate(LocalDate.of(2026, 8, 1));
        request.setEndBusinessDate(LocalDate.of(2026, 8, 26));
        return request;
    }

    private SettlementBatchDO batch(long id, int sequence) {
        SettlementBatchDO batch = new SettlementBatchDO();
        batch.setId(id);
        batch.setSettlementBatchNo("SB20260826-" + String.format("%08d", sequence));
        batch.setBusinessDate(LocalDate.of(2026, 8, 26));
        batch.setBusinessTimeZone("Asia/Shanghai");
        batch.setDailySequence(sequence);
        batch.setMerchantId("M-1");
        batch.setTargetCurrency("USD");
        batch.setTargetCurrencyExponent(2);
        batch.setBatchType("REGULAR");
        batch.setBatchStatus("POSTED");
        batch.setCandidateCount(1);
        batch.setRetryCount(0);
        batch.setVersion(0L);
        return batch;
    }
}
