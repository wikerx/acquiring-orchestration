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

/**
 * 对外提供手动交易或保证金结算任务命令，并编排可恢复的后台分段处理。
 */
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

    /**
     * 创建手动结算预审任务并固化候选范围，重复请求按幂等键返回同一任务。
     *
     * @param command 预审类型、候选筛选条件、目标币种和操作审计信息
     * @return 新建或幂等命中的预审任务结果
     */
    public TaskResult preview(PreviewCommand command) {
        TaskResult result = transactions.preview(command);
        LOGGER.info("手动结算预审任务已创建或幂等返回, event: SETTLEMENT_MANUAL_REVIEW_PREVIEW_END, taskNo: {}, reviewType: {}, taskStatus: {}, merchantId: {}, targetCurrency: {}, expectedCandidateCount: {}",
                result.taskNo(), result.reviewType(), result.taskStatus(), result.merchantId(),
                result.targetCurrency(), result.expectedCandidateCount());
        return result;
    }

    /**
     * 校验预审任务类型和版本后启动后台分段处理。
     *
     * @param taskNo 待启动的手动结算任务号
     * @param command 预期版本、启动原因和可信操作人审计信息
     * @param expectedReviewType 接口路径限定的预审类型，用于阻止跨类型任务启动
     * @return 启动后或幂等命中的任务结果
     */
    public TaskResult start(String taskNo, StartCommand command, String expectedReviewType) {
        TaskResult result = transactions.start(taskNo, command, expectedReviewType);
        LOGGER.info("手动结算预审任务已启动或幂等返回, event: SETTLEMENT_MANUAL_REVIEW_START_END, taskNo: {}, reviewType: {}, taskStatus: {}, expectedCandidateCount: {}",
                result.taskNo(), result.reviewType(), result.taskStatus(), result.expectedCandidateCount());
        return result;
    }

    /**
     * 查询指定类型手动结算任务的当前进度和处理结果。
     *
     * @param taskNo 可恢复后台任务号
     * @param expectedReviewType 接口路径限定的预审类型
     * @return 已持久化的任务快照
     */
    public TaskResult get(String taskNo, String expectedReviewType) {
        return transactions.get(taskNo, expectedReviewType);
    }

    /**
     * 领取一个到期任务并执行下一处理段；取消中的任务进入补偿路径。
     * 领取使用节点租约防止并发消费，单段异常由事务服务记录为可恢复状态。
     *
     * @return 成功领取到任务时返回 {@code true}，没有到期任务时返回 {@code false}
     */
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
            LOGGER.info("手动结算预审任务分段处理完成, event: SETTLEMENT_MANUAL_REVIEW_CHUNK_END, taskNo: {}, processingAction: {}",
                    task.getTaskNo(), "CANCELLING".equals(task.getTaskStatus())
                            ? "COMPENSATE" : "PROCESS");
        } catch (RuntimeException exception) {
            LOGGER.warn("手动结算预审任务处理失败，准备记录补偿状态, event: SETTLEMENT_MANUAL_REVIEW_CHUNK_FAILED, taskNo: {}, exceptionType: {}",
                    task.getTaskNo(), exception.getClass().getSimpleName());
            transactions.recordFailure(task.getTaskNo(), exception, maximumDuration, nowUtc());
        }
        return true;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
