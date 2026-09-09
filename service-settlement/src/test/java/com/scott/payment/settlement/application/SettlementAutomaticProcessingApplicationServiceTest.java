package com.scott.payment.settlement.application;

import com.scott.payment.finance.settlement.model.SettlementRateModels.CurrencyPair;
import com.scott.payment.finance.settlement.model.SettlementRateModels.LockedRate;
import com.scott.payment.finance.settlement.model.SettlementRateModels.QuoteDirection;
import com.scott.payment.finance.settlement.model.SettlementRateModels.RateMatrix;
import com.scott.payment.settlement.domain.model.SettlementFailureStage;
import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.dto.SettlementCurrency;
import com.scott.payment.settlement.dto.SettlementLockedRateMatrix;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.exception.SettlementProcessingException;
import com.scott.payment.settlement.service.SettlementAutomaticBatchService;
import com.scott.payment.settlement.service.SettlementBatchFailureService;
import com.scott.payment.settlement.service.SettlementBatchLeaseService;
import com.scott.payment.settlement.service.SettlementBatchRateLockService;
import com.scott.payment.settlement.service.SettlementCandidateActivationService;
import com.scott.payment.settlement.service.SettlementClearingFactService;
import com.scott.payment.settlement.service.SettlementResultCalculationService;
import com.scott.payment.settlement.service.SettlementLedgerPostingService;
import com.scott.payment.settlement.support.SettlementWorkerIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementAutomaticProcessingApplicationServiceTest
 * @date : 2026-08-26 23:58
 * @email : scott_x@163.com
 * @description : 验证服务启动后的候选准备、数据库租约、完整计算编排及独立失败补偿路径。
 * @status : create
 */
class SettlementAutomaticProcessingApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-26T08:00:00Z");
    private static final LocalDateTime NOW_UTC = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);

    private SettlementCandidateActivationService activationService;
    private SettlementAutomaticBatchService automaticBatchService;
    private SettlementBatchLeaseService leaseService;
    private SettlementClearingFactService factService;
    private SettlementBatchRateLockService rateLockService;
    private SettlementResultCalculationService resultService;
    private SettlementLedgerPostingService postingService;
    private SettlementBatchFailureService failureService;
    private SettlementWorkerIdentity workerIdentity;
    private SettlementAutomaticProcessingApplicationService service;

    @BeforeEach
    void setUp() {
        activationService = mock(SettlementCandidateActivationService.class);
        automaticBatchService = mock(SettlementAutomaticBatchService.class);
        leaseService = mock(SettlementBatchLeaseService.class);
        factService = mock(SettlementClearingFactService.class);
        rateLockService = mock(SettlementBatchRateLockService.class);
        resultService = mock(SettlementResultCalculationService.class);
        postingService = mock(SettlementLedgerPostingService.class);
        failureService = mock(SettlementBatchFailureService.class);
        workerIdentity = mock(SettlementWorkerIdentity.class);
        when(workerIdentity.value()).thenReturn("worker-1");
        service = new SettlementAutomaticProcessingApplicationService(
                activationService, automaticBatchService, leaseService, factService, rateLockService,
                resultService, postingService, failureService, workerIdentity,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    /** 准备任务应有界清空候选页并使用同一绝对时间创建最近成熟日批。 */
    @Test
    void shouldActivateCandidatesAndPrepareMaturedBatches() {
        when(activationService.activateEligibleCandidates(200, NOW_UTC)).thenReturn(200, 3);
        when(automaticBatchService.createAndClaimMaturedBatches(NOW)).thenReturn(2);

        SettlementAutomaticProcessingApplicationService.PreparationResult result = service.prepare();

        assertThat(result.activatedCandidateCount()).isEqualTo(203);
        assertThat(result.processedBatchCount()).isEqualTo(2);
        verify(activationService, org.mockito.Mockito.times(2))
                .activateEligibleCandidates(200, NOW_UTC);
        verify(automaticBatchService).createAndClaimMaturedBatches(NOW);
    }

    /** 取得租约后必须依次读取清分事实、复用或锁定矩阵并落结果，且不触发失败补偿。 */
    @Test
    void shouldProcessOneLeasedBatchThroughCalculatedResult() {
        SettlementBatchDO batch = batch();
        SettlementBatchFacts facts = facts();
        SettlementLockedRateMatrix rates = rates();
        when(leaseService.acquireNext("worker-1", NOW_UTC, NOW_UTC.plusMinutes(5)))
                .thenReturn(Optional.of(batch));
        when(factService.load(batch)).thenReturn(facts);
        when(rateLockService.lockOrLoad(batch, facts, "worker-1", NOW_UTC)).thenReturn(rates);
        when(resultService.calculateAndPersist(batch, facts, rates, "worker-1", NOW_UTC)).thenReturn(3);

        assertThat(service.processNext()).isTrue();

        var ordered = inOrder(factService, rateLockService, resultService);
        ordered.verify(factService).load(batch);
        ordered.verify(rateLockService).lockOrLoad(batch, facts, "worker-1", NOW_UTC);
        ordered.verify(resultService).calculateAndPersist(batch, facts, rates, "worker-1", NOW_UTC);
        verify(failureService, never()).recordFailure(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    /** 阶段异常必须交给独立失败服务记录，当前轮不得继续锁汇率或计算结果。 */
    @Test
    void shouldRecordStageFailureWithoutContinuingPipeline() {
        SettlementBatchDO batch = batch();
        SettlementProcessingException failure = new SettlementProcessingException(
                SettlementFailureStage.FACT_LOADING, "SETTLEMENT_FACT_MISSING", false,
                "settlement clearing fact is missing");
        when(leaseService.acquireNext("worker-1", NOW_UTC, NOW_UTC.plusMinutes(5)))
                .thenReturn(Optional.of(batch));
        when(factService.load(batch)).thenThrow(failure);

        assertThat(service.processNext()).isTrue();

        verify(failureService).recordFailure(batch.getSettlementBatchNo(), "worker-1", failure, NOW_UTC);
        verify(rateLockService, never()).lockOrLoad(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        verify(resultService, never()).calculateAndPersist(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    /** CALCULATED 批次取得新租约后应直接资金入账，禁止重复锁汇率或重算。 */
    @Test
    void shouldPostCalculatedBatchWithoutRecalculation() {
        SettlementBatchDO batch = batch();
        batch.setBatchStatus("CALCULATED");
        SettlementBatchFacts facts = facts();
        when(leaseService.acquireNext("worker-1", NOW_UTC, NOW_UTC.plusMinutes(5)))
                .thenReturn(Optional.of(batch));
        when(factService.load(batch)).thenReturn(facts);

        assertThat(service.processNext()).isTrue();

        verify(postingService).post(batch, facts, "worker-1", NOW_UTC);
        verify(rateLockService, never()).lockOrLoad(any(), any(), anyString(), any());
        verify(resultService, never()).calculateAndPersist(any(), any(), any(), anyString(), any());
    }

    /** 没有可租用批次时应立即结束，不读取清分事实。 */
    @Test
    void shouldStopWhenNoBatchIsAvailable() {
        when(leaseService.acquireNext("worker-1", NOW_UTC, NOW_UTC.plusMinutes(5)))
                .thenReturn(Optional.empty());

        assertThat(service.processNext()).isFalse();

        verify(factService, never()).load(org.mockito.ArgumentMatchers.any());
    }

    private SettlementBatchDO batch() {
        SettlementBatchDO row = new SettlementBatchDO();
        row.setSettlementBatchNo("SB20260826-00000001");
        row.setBatchStatus("CLAIMED");
        return row;
    }

    private SettlementBatchFacts facts() {
        return new SettlementBatchFacts(List.of(), List.of(), List.of(),
                Set.of(new SettlementCurrency("USD", 2)));
    }

    private SettlementLockedRateMatrix rates() {
        LockedRate identity = new LockedRate(new CurrencyPair("USD", "USD"),
                new BigDecimal("1.000000000000"), 2, 2, "SYSTEM_IDENTITY", null,
                QuoteDirection.DIRECT, NOW_UTC);
        return new SettlementLockedRateMatrix(RateMatrix.of(List.of(identity)), Map.of("USD", 1L));
    }
}
