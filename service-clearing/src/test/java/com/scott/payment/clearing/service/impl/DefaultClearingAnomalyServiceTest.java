package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.domain.model.ClearingOperationFacts;
import com.scott.payment.clearing.domain.state.ClearingAnomalyTypeEnum;
import com.scott.payment.clearing.entity.ClearingTransactionAbnormalEventDO;
import com.scott.payment.clearing.mapper.ClearingTransactionAbnormalEventMapper;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingAnomalyServiceTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证清分异常复用交易异常案件表并携带确定性去重键和精确分片时间。
 * @status : create
 */
class DefaultClearingAnomalyServiceTest {

    @Test
    void shouldUpsertSanitizedClearingCaseAndMetric() {
        ClearingTransactionAbnormalEventMapper mapper = mock(ClearingTransactionAbnormalEventMapper.class);
        ClearingOperationalMetrics metrics = mock(ClearingOperationalMetrics.class);
        DefaultClearingAnomalyService service = new DefaultClearingAnomalyService(mapper, metrics);
        ClearingOperationFacts operation = operation();
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 0);
        when(mapper.upsertOccurrence(org.mockito.ArgumentMatchers.any())).thenReturn(1);

        service.record(operation, "FS-1", 2, ClearingAnomalyTypeEnum.FINANCIAL_MISMATCH,
                "AMOUNT_INVALID", "amount mismatch\nwithout sensitive payload", now);

        ArgumentCaptor<ClearingTransactionAbnormalEventDO> captor =
                ArgumentCaptor.forClass(ClearingTransactionAbnormalEventDO.class);
        verify(mapper).upsertOccurrence(captor.capture());
        ClearingTransactionAbnormalEventDO row = captor.getValue();
        assertThat(row.getAbnormalEventId()).startsWith("CA");
        assertThat(row.getDeduplicationKey()).hasSize(64);
        assertThat(row.getTransactionDateTime()).isEqualTo(operation.transactionDateTime());
        assertThat(row.getSourceRecordId()).isEqualTo("FS-1:2");
        assertThat(row.getAbnormalDescription()).doesNotContain("\n");
        verify(metrics).recordAnomaly(ClearingAnomalyTypeEnum.FINANCIAL_MISMATCH);
        verify(metrics).recordAmountImbalance("USD");
    }

    @Test
    void shouldResolveOnlyClearingCasesForExactTransactionShard() {
        ClearingTransactionAbnormalEventMapper mapper = mock(ClearingTransactionAbnormalEventMapper.class);
        DefaultClearingAnomalyService service = new DefaultClearingAnomalyService(
                mapper, mock(ClearingOperationalMetrics.class));
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 26, 10, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 0);

        service.resolve("TX-1", transactionTime, "FS-1:3", now);

        verify(mapper).resolveActiveClearingCases("TX-1", transactionTime, "FS-1:3", now);
    }

    private ClearingOperationFacts operation() {
        LocalDateTime transactionTime = LocalDateTime.of(2026, 8, 26, 10, 0);
        return new ClearingOperationFacts(
                "TX-1", "OP-1", null, "M-1", "ORDER-1", "PAYMENT", "SUCCESS",
                "USD", new BigDecimal("100.00"), "USD", new BigDecimal("100.00"),
                "USD", new BigDecimal("100.00"), 2, transactionTime,
                LocalDateTime.of(2026, 8, 26, 2, 0), "Asia/Shanghai", 1);
    }
}
