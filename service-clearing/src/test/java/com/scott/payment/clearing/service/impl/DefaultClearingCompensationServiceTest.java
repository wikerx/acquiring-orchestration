package com.scott.payment.clearing.service.impl;

import com.scott.payment.clearing.api.internal.dto.ClearingCompensationDTOs.CompensationScanRequest;
import com.scott.payment.clearing.api.internal.dto.ClearingCompensationDTOs.CompensationScanResponse;
import com.scott.payment.clearing.entity.ClearingCompensationCandidateDO;
import com.scott.payment.clearing.mapper.ClearingCompensationMapper;
import com.scott.payment.clearing.service.ClearingRecoveryService;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultClearingCompensationServiceTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证清分补偿按独立事务逐条恢复、游标分页、影子执行和失败批次可观测性
 * @status : create
 */
class DefaultClearingCompensationServiceTest {

    @Test
    void scanMustNotHoldAnOuterTransactionAcrossIndependentRecoveries() throws NoSuchMethodException {
        Method scan = DefaultClearingCompensationService.class.getMethod(
                "scan", CompensationScanRequest.class, LocalDateTime.class);

        assertThat(scan.getAnnotation(Transactional.class)).isNull();
    }

    @Test
    void dryRunClassifiesCandidatesWithoutWriting() {
        ClearingCompensationMapper mapper = mock(ClearingCompensationMapper.class);
        ClearingRecoveryService recoveryService = mock(ClearingRecoveryService.class);
        DefaultClearingCompensationService service = new DefaultClearingCompensationService(
                mapper, recoveryService, mock(ClearingOperationalMetrics.class));
        LocalDateTime begin = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 0);
        CompensationScanRequest request = request("DRY_RUN", begin, end, 2);
        ClearingCompensationCandidateDO missing = candidate(11L, "TX11", null, null);
        ClearingCompensationCandidateDO expired = candidate(12L, "TX12", "FS12", "PROCESSING_TIMEOUT");
        when(mapper.selectCandidates(begin, end, null, null, now.minusMinutes(5), now, 3))
                .thenReturn(List.of(missing, expired));

        CompensationScanResponse result = service.scan(request, now);

        assertThat(result.getScannedCount()).isEqualTo(2);
        assertThat(result.getWriteCount()).isZero();
        assertThat(result.getRecords()).extracting("reason")
                .containsExactly("MISSING_FINANCE_STATE", "PROCESSING_TIMEOUT");
        verify(recoveryService, never()).recover(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shadowWriteRecoversEachCandidateInItsOwnBoundaryAndReturnsCursor() {
        ClearingCompensationMapper mapper = mock(ClearingCompensationMapper.class);
        ClearingRecoveryService recoveryService = mock(ClearingRecoveryService.class);
        DefaultClearingCompensationService service = new DefaultClearingCompensationService(
                mapper, recoveryService, mock(ClearingOperationalMetrics.class));
        LocalDateTime begin = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 0);
        CompensationScanRequest request = request("SHADOW_WRITE", begin, end, 1);
        ClearingCompensationCandidateDO first = candidate(21L, "TX21", "FS21", "FAILED_DUE");
        ClearingCompensationCandidateDO lookAhead = candidate(22L, "TX22", "FS22", "FAILED_DUE");
        when(mapper.selectCandidates(begin, end, null, null, now.minusMinutes(5), now, 2))
                .thenReturn(List.of(first, lookAhead));
        when(recoveryService.recover(first, now)).thenReturn("RETRY_SCHEDULED");

        CompensationScanResponse result = service.scan(request, now);

        assertThat(result.getScannedCount()).isEqualTo(1);
        assertThat(result.getWriteCount()).isEqualTo(1);
        assertThat(result.isHasMore()).isTrue();
        assertThat(result.getNextCursorId()).isEqualTo(21L);
        verify(recoveryService).recover(first, now);
        verify(recoveryService, never()).recover(lookAhead, now);
    }

    @Test
    void shadowWriteShouldRecoverEveryCandidate() {
        ClearingCompensationMapper mapper = mock(ClearingCompensationMapper.class);
        ClearingRecoveryService recoveryService = mock(ClearingRecoveryService.class);
        DefaultClearingCompensationService service = new DefaultClearingCompensationService(
                mapper, recoveryService, mock(ClearingOperationalMetrics.class));
        LocalDateTime begin = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 0);
        CompensationScanRequest request = request("SHADOW_WRITE", begin, end, 1);
        ClearingCompensationCandidateDO candidate = candidate(31L, "TX31", "FS31", "FAILED_DUE");
        when(mapper.selectCandidates(begin, end, null, null, now.minusMinutes(5), now, 2))
                .thenReturn(List.of(candidate));
        when(recoveryService.recover(candidate, now)).thenReturn("RETRY_SCHEDULED");

        CompensationScanResponse result = service.scan(request, now);

        assertThat(result.getWriteCount()).isEqualTo(1);
        assertThat(result.getSkippedCount()).isZero();
        assertThat(result.getRecords()).extracting("result").containsExactly("RETRY_SCHEDULED");
        verify(recoveryService).recover(candidate, now);
    }

    @Test
    void operationalFailureShouldRecordFailedBatchWithoutHidingException() {
        ClearingCompensationMapper mapper = mock(ClearingCompensationMapper.class);
        ClearingOperationalMetrics metrics = mock(ClearingOperationalMetrics.class);
        DefaultClearingCompensationService service = new DefaultClearingCompensationService(
                mapper, mock(ClearingRecoveryService.class), metrics);
        LocalDateTime begin = LocalDateTime.of(2026, 7, 1, 0, 0);
        LocalDateTime end = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime now = LocalDateTime.of(2026, 8, 26, 12, 0);
        when(mapper.selectCandidates(begin, end, null, null, now.minusMinutes(5), now, 2))
                .thenThrow(new IllegalStateException("replica unavailable"));

        assertThatThrownBy(() -> service.scan(request("SHADOW_WRITE", begin, end, 1), now))
                .isInstanceOf(IllegalStateException.class);

        verify(metrics).recordCompensationFailure("SHADOW_WRITE");
    }

    private CompensationScanRequest request(String mode, LocalDateTime begin, LocalDateTime end, int limit) {
        CompensationScanRequest request = new CompensationScanRequest();
        request.setMode(mode);
        request.setBeginTime(begin);
        request.setEndTime(end);
        request.setLimit(limit);
        return request;
    }

    private ClearingCompensationCandidateDO candidate(Long rowId,
                                                       String transactionId,
                                                       String financeStateId,
                                                       String reason) {
        ClearingCompensationCandidateDO row = new ClearingCompensationCandidateDO();
        row.setOperationRowId(rowId);
        row.setTransactionId(transactionId);
        row.setMerchantId("M-" + rowId);
        row.setTransactionDateTime(LocalDateTime.of(2026, 8, 26, 10, 0).plusSeconds(rowId));
        row.setFinanceStateId(financeStateId);
        row.setReason(reason);
        return row;
    }
}
