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
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementAutomaticProcessingApplicationService
 * @date : 2026-08-26 23:50
 * @email : scott_x@163.com
 * @description : 自动激活、建批、租约、事实读取、汇率锁定和结果计算的应用编排；明确不调用余额或资金流水服务。
 * @status : create
 */
@Service
public class SettlementAutomaticProcessingApplicationService {

    /**
     * {@code ACTIVATION_PAGE_SIZE}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int ACTIVATION_PAGE_SIZE = 200;
    /**
     * {@code MAX_ACTIVATION_PAGES_PER_RUN}，用于控制分页查询、批量扫描或任务单次处理规模。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与查询条件和时间范围共同控制分页或扫描窗口。
     * </p>
     */
    private static final int MAX_ACTIVATION_PAGES_PER_RUN = 10;
    /**
     * {@code PROCESSING_LEASE}常量，统一 {@code SettlementAutomaticProcessingApplicationService} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
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

    /** 创建使用 UTC 时钟的自动结算应用服务。 */
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
            if (status == SettlementBatchStatus.CALCULATED || postingRetry) {
                postingService.post(batch, facts, owner, nowUtc());
            } else {
                SettlementLockedRateMatrix rates = rateLockService.lockOrLoad(
                        batch, facts, owner, nowUtc());
                resultService.calculateAndPersist(batch, facts, rates, owner, nowUtc());
            }
        } catch (RuntimeException failure) {
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
