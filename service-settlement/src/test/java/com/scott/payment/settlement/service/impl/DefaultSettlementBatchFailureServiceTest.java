package com.scott.payment.settlement.service.impl;

import com.scott.payment.settlement.domain.model.SettlementFailureStage;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.exception.SettlementProcessingException;
import com.scott.payment.settlement.mapper.SettlementBatchCandidateMapper;
import com.scott.payment.settlement.mapper.SettlementBatchMapper;
import com.scott.payment.settlement.mapper.SettlementCandidateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultSettlementBatchFailureServiceTest
 * @date : 2026-08-26 23:58
 * @email : scott_x@163.com
 * @description : 验证结算失败按稳定错误属性退避，并在不可重试或次数耗尽时原子转人工复核。
 * @status : create
 */
class DefaultSettlementBatchFailureServiceTest {

    private SettlementBatchMapper batchMapper;
    private SettlementCandidateMapper candidateMapper;
    private SettlementBatchCandidateMapper relationMapper;
    private DefaultSettlementBatchFailureService service;

    @BeforeEach
    void setUp() {
        batchMapper = mock(SettlementBatchMapper.class);
        candidateMapper = mock(SettlementCandidateMapper.class);
        relationMapper = mock(SettlementBatchCandidateMapper.class);
        service = new DefaultSettlementBatchFailureService(batchMapper, candidateMapper, relationMapper);
    }

    /** 首次可重试失败应释放租约并写入一分钟后的数据库重试时间。 */
    @Test
    void shouldScheduleBoundedRetryAndSanitizeMessage() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 16, 0);
        SettlementBatchDO batch = batch(now, 0, 2);
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(batchMapper.recordRetryableFailure(
                batch.getSettlementBatchNo(), "worker-1", 0, 9L,
                "FACT_LOADING", "SETTLEMENT_FACT_TEMPORARY", "temporary fact failure",
                now, now.plusMinutes(1))).thenReturn(1);

        service.recordFailure(" " + batch.getSettlementBatchNo() + " ", " worker-1 ",
                new SettlementProcessingException(SettlementFailureStage.FACT_LOADING,
                        "SETTLEMENT_FACT_TEMPORARY", true, "temporary\nfact\rfailure"), now);

        verify(batchMapper).recordRetryableFailure(
                batch.getSettlementBatchNo(), "worker-1", 0, 9L,
                "FACT_LOADING", "SETTLEMENT_FACT_TEMPORARY", "temporary fact failure",
                now, now.plusMinutes(1));
        verify(candidateMapper, never()).markBatchManualReview(batch.getSettlementBatchNo(), now);
        verify(relationMapper, never()).markBatchManualReview(batch.getSettlementBatchNo(), now);
    }

    /** 稳定事实冲突不得盲目重试，批次、候选和审计关系必须一起进入人工复核。 */
    @Test
    void shouldMoveStableConflictAndAllRelationsToManualReview() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 16, 0);
        SettlementBatchDO batch = batch(now, 3, 2);
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(batchMapper.recordManualReview(
                batch.getSettlementBatchNo(), "worker-1", 3, 9L,
                "RATE_LOCKING", "SETTLEMENT_RATE_MATRIX_PARTIAL", "stored rate matrix is partial", now))
                .thenReturn(1);
        when(candidateMapper.markBatchManualReview(batch.getSettlementBatchNo(), now)).thenReturn(2);
        when(relationMapper.markBatchManualReview(batch.getSettlementBatchNo(), now)).thenReturn(2);

        service.recordFailure(batch.getSettlementBatchNo(), "worker-1",
                new SettlementProcessingException(SettlementFailureStage.RATE_LOCKING,
                        "SETTLEMENT_RATE_MATRIX_PARTIAL", false, "stored rate matrix is partial"), now);

        verify(batchMapper).recordManualReview(
                batch.getSettlementBatchNo(), "worker-1", 3, 9L,
                "RATE_LOCKING", "SETTLEMENT_RATE_MATRIX_PARTIAL", "stored rate matrix is partial", now);
        verify(candidateMapper).markBatchManualReview(batch.getSettlementBatchNo(), now);
        verify(relationMapper).markBatchManualReview(batch.getSettlementBatchNo(), now);
    }

    /** 第八次自动重试后再次失败必须停止循环并使用统一耗尽错误码转人工复核。 */
    @Test
    void shouldStopRetryingAfterMaximumRetryCount() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 16, 0);
        SettlementBatchDO batch = batch(now, 8, 1);
        when(batchMapper.selectByBatchNoForUpdate(batch.getSettlementBatchNo())).thenReturn(batch);
        when(batchMapper.recordManualReview(
                batch.getSettlementBatchNo(), "worker-1", 8, 9L,
                "RESULT_CALCULATION", "SETTLEMENT_RETRY_EXHAUSTED",
                "unexpected settlement processing failure: IllegalStateException", now)).thenReturn(1);
        when(candidateMapper.markBatchManualReview(batch.getSettlementBatchNo(), now)).thenReturn(1);
        when(relationMapper.markBatchManualReview(batch.getSettlementBatchNo(), now)).thenReturn(1);

        service.recordFailure(batch.getSettlementBatchNo(), "worker-1",
                new IllegalStateException("sensitive database detail"), now);

        verify(batchMapper).recordManualReview(
                batch.getSettlementBatchNo(), "worker-1", 8, 9L,
                "RESULT_CALCULATION", "SETTLEMENT_RETRY_EXHAUSTED",
                "unexpected settlement processing failure: IllegalStateException", now);
        verify(batchMapper, never()).recordRetryableFailure(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private SettlementBatchDO batch(LocalDateTime now, int retryCount, int candidateCount) {
        SettlementBatchDO row = new SettlementBatchDO();
        row.setSettlementBatchNo("SB20260826-00000001");
        row.setBatchStatus("RATE_LOCKED");
        row.setCandidateCount(candidateCount);
        row.setRetryCount(retryCount);
        row.setProcessingOwner("worker-1");
        row.setProcessingDeadline(now.plusMinutes(5));
        row.setVersion(9L);
        return row;
    }
}
