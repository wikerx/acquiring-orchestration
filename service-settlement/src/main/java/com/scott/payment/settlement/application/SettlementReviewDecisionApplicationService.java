package com.scott.payment.settlement.application;

import com.scott.payment.settlement.dto.SettlementReviewDecisionCommand;
import com.scott.payment.settlement.dto.SettlementCommandAudit;
import com.scott.payment.settlement.dto.SettlementReviewDecisionModels.TaskResult;
import com.scott.payment.settlement.entity.SettlementReviewDecisionTaskDO;
import com.scott.payment.settlement.support.SettlementWorkerIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

/** 提交、查询并调度大批量预审单的异步 Maker-Checker 决策。 */
@Service
public class SettlementReviewDecisionApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SettlementReviewDecisionApplicationService.class);

    private final SettlementReviewDecisionTransactionService transactions;
    private final SettlementWorkerIdentity workerIdentity;
    private final Duration leaseDuration;
    private final Duration maximumDuration;
    private final Clock clock;

    @Autowired
    public SettlementReviewDecisionApplicationService(
            SettlementReviewDecisionTransactionService transactions,
            SettlementWorkerIdentity workerIdentity,
            @Value("${settlement.review-decision.lease-seconds:120}") long leaseSeconds,
            @Value("${settlement.review-decision.maximum-minutes:30}") long maximumMinutes) {
        this(transactions, workerIdentity, leaseSeconds, maximumMinutes, Clock.systemUTC());
    }

    SettlementReviewDecisionApplicationService(
            SettlementReviewDecisionTransactionService transactions,
            SettlementWorkerIdentity workerIdentity,
            long leaseSeconds,
            long maximumMinutes,
            Clock clock) {
        this.transactions = transactions;
        this.workerIdentity = workerIdentity;
        this.leaseDuration = Duration.ofSeconds(Math.max(30, Math.min(leaseSeconds, 600)));
        this.maximumDuration = Duration.ofMinutes(Math.max(5, Math.min(maximumMinutes, 120)));
        this.clock = Objects.requireNonNull(clock, "settlement review decision clock is required");
    }

    public TaskResult submit(String reviewOrderNo, SettlementReviewDecisionCommand command) {
        return transactions.submit(reviewOrderNo, command);
    }

    public TaskResult get(String taskNo) {
        return transactions.get(taskNo);
    }

    public TaskResult resume(String taskNo, long expectedVersion, SettlementCommandAudit audit) {
        return transactions.resume(taskNo, expectedVersion, audit);
    }

    public boolean processNext() {
        LocalDateTime now = nowUtc();
        String owner = workerIdentity.value();
        Optional<SettlementReviewDecisionTaskDO> claimed = transactions.claimNext(
                owner, now, now.plus(leaseDuration));
        if (claimed.isEmpty()) {
            return false;
        }
        SettlementReviewDecisionTaskDO task = claimed.get();
        try {
            transactions.processClaimed(task.getTaskNo(), owner, nowUtc());
        } catch (RuntimeException exception) {
            LOGGER.warn("Settlement review decision task failed, taskNo={}, failureType={}",
                    task.getTaskNo(), exception.getClass().getSimpleName());
            transactions.recordFailure(task.getTaskNo(), exception, maximumDuration, nowUtc());
        }
        return true;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
