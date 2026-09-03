package com.scott.payment.clearing.support;

import com.scott.payment.clearing.entity.ClearingReserveStateDO;
import com.scott.payment.clearing.mapper.ClearingReserveMapper;
import com.scott.payment.clearing.service.ReserveReleaseService;
import com.scott.payment.clearing.service.ReserveReleaseService.ReserveReleaseOutcome;
import com.scott.payment.clearing.service.ReserveReleaseService.ReserveReleaseResult;
import com.scott.payment.component.db.sharding.TransactionShardingProperties;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ReserveReleaseScanServiceTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证保证金释放扫描只遍历已发布历史季度，并隔离单条释放失败。
 * @status : create
 */
class ReserveReleaseScanServiceTest {

    @Test
    void scanShouldRoutePublishedQuartersAndContinueAfterSingleFailure() {
        ClearingReserveMapper mapper = mock(ClearingReserveMapper.class);
        ReserveReleaseService releaseService = mock(ReserveReleaseService.class);
        ClearingOperationalMetrics metrics = mock(ClearingOperationalMetrics.class);
        TransactionShardingProperties properties = new TransactionShardingProperties();
        properties.setPhysicalNodes(List.of("202601", "202602", "202701"));
        Instant instant = Instant.parse("2026-08-26T10:30:00Z");
        Clock clock = Clock.fixed(instant, ZoneOffset.UTC);
        ReserveReleaseScanService service = new ReserveReleaseScanService(
                mapper, releaseService, properties, metrics, clock);
        ClearingReserveStateDO first = candidate("RS-1", "PAY-1",
                LocalDateTime.of(2026, 1, 1, 10, 0));
        ClearingReserveStateDO second = candidate("RS-2", "PAY-2",
                LocalDateTime.of(2026, 5, 1, 10, 0));
        when(mapper.selectDueReleaseCandidates(
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDate.of(2026, 8, 26), 200)).thenReturn(List.of(first));
        when(mapper.selectDueReleaseCandidates(
                LocalDateTime.of(2026, 4, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDate.of(2026, 8, 26), 200)).thenReturn(List.of(second));
        when(releaseService.release("RS-1", "PAY-1", first.getTransactionDateTime(), instant))
                .thenThrow(new IllegalStateException("bad candidate"));
        when(releaseService.release("RS-2", "PAY-2", second.getTransactionDateTime(), instant))
                .thenReturn(new ReserveReleaseResult(ReserveReleaseOutcome.RELEASED, "RRL-2", 2));

        ReserveReleaseScanService.ReserveReleaseScanResult result = service.scan();

        assertThat(result.scanned()).isEqualTo(2);
        assertThat(result.released()).isEqualTo(1);
        assertThat(result.failed()).isEqualTo(1);
        verify(mapper).selectDueReleaseCandidates(
                LocalDateTime.of(2026, 1, 1, 0, 0), LocalDateTime.of(2026, 4, 1, 0, 0),
                LocalDate.of(2026, 8, 26), 200);
        verify(mapper).selectDueReleaseCandidates(
                LocalDateTime.of(2026, 4, 1, 0, 0), LocalDateTime.of(2026, 7, 1, 0, 0),
                LocalDate.of(2026, 8, 26), 200);
        verify(releaseService).release("RS-2", "PAY-2", second.getTransactionDateTime(), instant);
        verify(metrics).recordReserveRelease("FAILED");
        verify(metrics).recordReserveRelease("RELEASED");
        verifyNoMoreInteractions(mapper);
    }

    private ClearingReserveStateDO candidate(String stateId,
                                             String transactionId,
                                             LocalDateTime transactionTime) {
        ClearingReserveStateDO row = new ClearingReserveStateDO();
        row.setReserveStateId(stateId);
        row.setOriginalTransactionId(transactionId);
        row.setTransactionDateTime(transactionTime);
        return row;
    }
}
