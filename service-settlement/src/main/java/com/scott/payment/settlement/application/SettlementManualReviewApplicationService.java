package com.scott.payment.settlement.application;

import com.scott.payment.settlement.dto.SettlementManualReviewModels.PreviewCommand;
import com.scott.payment.settlement.dto.SettlementManualReviewModels.StartCommand;
import com.scott.payment.settlement.dto.SettlementManualReviewModels.TaskResult;
import com.scott.payment.settlement.entity.SettlementManualReviewTaskDO;
import com.scott.payment.settlement.support.SettlementWorkerIdentity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.Optional;

/** 对外提供手动交易或保证金结算任务命令，并编排可恢复的后台分段处理。 */
@Service
public class SettlementManualReviewApplicationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(
            SettlementManualReviewApplicationService.class);

    private final SettlementManualReviewTransactionService transactions;
    private final SettlementWorkerIdentity workerIdentity;
    private final int chunkSize;
    private final Duration leaseDuration;
    private final Duration maximumDuration;
    private final Clock clock;

    @Autowired
    public SettlementManualReviewApplicationService(
            SettlementManualReviewTransactionService transactions,
            SettlementWorkerIdentity workerIdentity,
            @Value("${settlement.manual-review.chunk-size:500}") int chunkSize,
            @Value("${settlement.manual-review.lease-seconds:120}") long leaseSeconds,
            @Value("${settlement.manual-review.maximum-minutes:30}") long maximumMinutes) {
        this(transactions, workerIdentity, chunkSize, leaseSeconds, maximumMinutes, Clock.systemUTC());
    }

    SettlementManualReviewApplicationService(
            SettlementManualReviewTransactionService transactions,
            SettlementWorkerIdentity workerIdentity,
            int chunkSize,
            long leaseSeconds,
            long maximumMinutes,
            Clock clock) {
        this.transactions = transactions;
        this.workerIdentity = workerIdentity;
        this.chunkSize = Math.max(50, Math.min(chunkSize, 1000));
        this.leaseDuration = Duration.ofSeconds(Math.max(30, Math.min(leaseSeconds, 600)));
        this.maximumDuration = Duration.ofMinutes(Math.max(5, Math.min(maximumMinutes, 120)));
        this.clock = Objects.requireNonNull(clock, "settlement manual review clock is required");
    }

    public TaskResult preview(PreviewCommand command) {
        return transactions.preview(command);
    }

    public TaskResult start(String taskNo, StartCommand command, String expectedReviewType) {
        return transactions.start(taskNo, command, expectedReviewType);
    }

    public TaskResult get(String taskNo, String expectedReviewType) {
        return transactions.get(taskNo, expectedReviewType);
    }

    public boolean processNext() {
        LocalDateTime now = nowUtc();
        String owner = workerIdentity.value();
        Optional<SettlementManualReviewTaskDO> claimed = transactions.claimNext(
                owner, now, now.plus(leaseDuration));
        if (claimed.isEmpty()) {
            return false;
        }
        SettlementManualReviewTaskDO task = claimed.get();
        try {
            if ("CANCELLING".equals(task.getTaskStatus())) {
                transactions.compensateClaimed(task.getTaskNo(), owner, nowUtc());
            } else {
                transactions.processClaimed(task.getTaskNo(), owner, chunkSize, nowUtc());
            }
        } catch (RuntimeException exception) {
            LOGGER.warn("Manual settlement review task processing failed, taskNo={}, failureType={}",
                    task.getTaskNo(), exception.getClass().getSimpleName());
            transactions.recordFailure(task.getTaskNo(), exception, maximumDuration, nowUtc());
        }
        return true;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
