package com.scott.payment.settlement.application;

import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.dto.SettlementLockedRateMatrix;
import com.scott.payment.settlement.domain.model.SettlementBatchStatus;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.service.SettlementAutomaticBatchService;
import com.scott.payment.settlement.service.SettlementBatchFailureService;
import com.scott.payment.settlement.service.SettlementBatchLeaseService;
import com.scott.payment.settlement.service.SettlementBatchRateLockService;
import com.scott.payment.settlement.service.SettlementCandidateActivationService;
import com.scott.payment.settlement.service.SettlementClearingFactService;
import com.scott.payment.settlement.service.SettlementResultCalculationService;
import com.scott.payment.settlement.service.SettlementLedgerPostingService;
import com.scott.payment.settlement.support.SettlementWorkerIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

/**
 * 自动结算批次处理编排。
 *
 * <p>候选激活、建批和批次处理均依赖数据库状态与租约实现多实例排他；本类只决定处理阶段，
 * 金额计算、汇率锁定和资金入账分别由对应领域服务保证事务与幂等。</p>
 */
@Service
public class SettlementAutomaticProcessingApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SettlementAutomaticProcessingApplicationService.class);

    /**
     * 单页激活上限，与数据库批量更新规模保持一致。
     */
    private static final int ACTIVATION_PAGE_SIZE = 200;
    /**
     * 单轮激活页数上限，防止一次调度长期占用工作线程。
     */
    private static final int MAX_ACTIVATION_PAGES_PER_RUN = 10;
    /**
     * 批次处理租约，超时后允许其他实例重新领取。
     */
    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(5);

    private final SettlementCandidateActivationService activationService;
    private final SettlementAutomaticBatchService automaticBatchService;
    private final SettlementBatchLeaseService leaseService;
    private final SettlementClearingFactService factService;
    private final SettlementBatchRateLockService rateLockService;
    private final SettlementResultCalculationService resultService;
    private final SettlementLedgerPostingService postingService;
    private final SettlementBatchFailureService failureService;
    private final SettlementWorkerIdentity workerIdentity;
    private final Clock clock;

    /**
     * 创建使用 UTC 时钟的自动结算应用服务。
     */
    @Autowired
    public SettlementAutomaticProcessingApplicationService(
            SettlementCandidateActivationService activationService,
            SettlementAutomaticBatchService automaticBatchService,
            SettlementBatchLeaseService leaseService,
            SettlementClearingFactService factService,
            SettlementBatchRateLockService rateLockService,
            SettlementResultCalculationService resultService,
            SettlementLedgerPostingService postingService,
            SettlementBatchFailureService failureService,
            SettlementWorkerIdentity workerIdentity) {
        this(activationService, automaticBatchService, leaseService, factService, rateLockService,
                resultService, postingService, failureService, workerIdentity, Clock.systemUTC());
    }

    SettlementAutomaticProcessingApplicationService(
            SettlementCandidateActivationService activationService,
            SettlementAutomaticBatchService automaticBatchService,
            SettlementBatchLeaseService leaseService,
            SettlementClearingFactService factService,
            SettlementBatchRateLockService rateLockService,
            SettlementResultCalculationService resultService,
            SettlementLedgerPostingService postingService,
            SettlementBatchFailureService failureService,
            SettlementWorkerIdentity workerIdentity,
            Clock clock) {
        this.activationService = activationService;
        this.automaticBatchService = automaticBatchService;
        this.leaseService = leaseService;
        this.factService = factService;
        this.rateLockService = rateLockService;
        this.resultService = resultService;
        this.postingService = postingService;
        this.failureService = failureService;
        this.workerIdentity = workerIdentity;
        this.clock = Objects.requireNonNull(clock, "settlement automatic clock is required");
    }

    /**
     * 有界激活合法候选并创建最近成熟日批；确定性请求键保证多实例和重复调度安全。
     *
     * @return 本轮激活候选数与进入认领流程的批次数
     */
    public PreparationResult prepare() {
        Instant now = clock.instant();
        LocalDateTime nowUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        int activated = 0;
        for (int page = 0; page < MAX_ACTIVATION_PAGES_PER_RUN; page++) {
            int current = activationService.activateEligibleCandidates(ACTIVATION_PAGE_SIZE, nowUtc);
            activated += current;
            if (current < ACTIVATION_PAGE_SIZE) {
                break;
            }
        }
        int batches = automaticBatchService.createAndClaimMaturedBatches(now);
        LOGGER.info("自动结算准备完成, event: SETTLEMENT_AUTOMATIC_PREPARE_END, activatedCandidateCount: {}, claimedBatchCount: {}",
                activated, batches);
        return new PreparationResult(activated, batches);
    }

    /**
     * 获取并处理一条数据库租约；任何失败先回滚当前阶段，再由独立事务记录退避或人工复核。
     *
     * @return 是否取得过一条批次
     */
    public boolean processNext() {
        LocalDateTime leaseTime = nowUtc();
        String owner = workerIdentity.value();
        Optional<SettlementBatchDO> leased = leaseService.acquireNext(
                owner, leaseTime, leaseTime.plus(PROCESSING_LEASE));
        if (leased.isEmpty()) {
            return false;
        }
        SettlementBatchDO batch = leased.get();
        try {
            SettlementBatchFacts facts = factService.load(batch);
            SettlementBatchStatus status = SettlementBatchStatus.valueOf(batch.getBatchStatus());
            boolean postingRetry = status == SettlementBatchStatus.FAILED_RETRYABLE
                    && "LEDGER_POSTING".equals(batch.getLastFailureStage());
            int processedCount;
            String processingStage;
            if (status == SettlementBatchStatus.CALCULATED || postingRetry) {
                processedCount = postingService.post(batch, facts, owner, nowUtc());
                processingStage = "LEDGER_POSTING";
            } else {
                SettlementLockedRateMatrix rates = rateLockService.lockOrLoad(
                        batch, facts, owner, nowUtc());
                processedCount = resultService.calculateAndPersist(batch, facts, rates, owner, nowUtc());
                processingStage = "RESULT_CALCULATION";
            }
            LOGGER.info("自动结算批次处理完成, event: SETTLEMENT_AUTOMATIC_BATCH_END, settlementBatchNo: {}, processingStage: {}, processedCount: {}",
                    batch.getSettlementBatchNo(), processingStage, processedCount);
        } catch (RuntimeException failure) {
            LOGGER.warn("自动结算批次处理失败，准备记录补偿状态, event: SETTLEMENT_AUTOMATIC_BATCH_FAILED, settlementBatchNo: {}, batchStatus: {}, exceptionType: {}",
                    batch.getSettlementBatchNo(), batch.getBatchStatus(),
                    failure.getClass().getSimpleName());
            failureService.recordFailure(batch.getSettlementBatchNo(), owner, failure, nowUtc());
        }
        return true;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }

    /**
     * @param activatedCandidateCount 本轮激活候选数
     * @param processedBatchCount 本轮进入认领流程的批次数
     */
    public record PreparationResult(int activatedCandidateCount, int processedBatchCount) {
    }
}
