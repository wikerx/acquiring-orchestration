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

/**
 * 提交、查询并调度大批量预审单的异步 Maker-Checker 决策。
 */
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

    /**
     * 按请求幂等键提交复核决策任务，并返回已持久化的任务快照。
     *
     * @param reviewOrderNo 待执行决策的结算复核单号
     * @param command 决策动作、预期版本和可信操作人审计信息
     * @return 新建或幂等命中的决策任务结果
     */
    public TaskResult submit(String reviewOrderNo, SettlementReviewDecisionCommand command) {
        TaskResult result = transactions.submit(reviewOrderNo, command);
        LOGGER.info("结算复核决策任务已提交或幂等返回, event: SETTLEMENT_REVIEW_DECISION_SUBMIT_END, taskNo: {}, reviewOrderNo: {}, decisionAction: {}, taskStatus: {}, totalSegmentCount: {}",
                result.taskNo(), result.reviewOrderNo(), result.decisionAction(),
                result.taskStatus(), result.totalSegmentCount());
        return result;
    }

    /**
     * 查询指定决策任务的当前进度和处理结果。
     *
     * @param taskNo 可恢复后台决策任务号
     * @return 已持久化的任务快照
     */
    public TaskResult get(String taskNo) {
        return transactions.get(taskNo);
    }

    /**
     * 在乐观锁版本匹配时恢复失败或超时的决策任务。
     * 相同恢复请求由审计幂等键收敛，避免并发操作重复推进任务。
     *
     * @param taskNo 待恢复的后台决策任务号
     * @param expectedVersion 调用方读取到的任务版本，用于阻止并发覆盖
     * @param audit 可信操作人、请求幂等键、恢复原因和客户端审计信息
     * @return 恢复后或幂等命中的任务结果
     */
    public TaskResult resume(String taskNo, long expectedVersion, SettlementCommandAudit audit) {
        TaskResult result = transactions.resume(taskNo, expectedVersion, audit);
        LOGGER.info("结算复核决策任务已恢复或幂等返回, event: SETTLEMENT_REVIEW_DECISION_RESUME_END, taskNo: {}, reviewOrderNo: {}, taskStatus: {}, processedSegmentCount: {}, totalSegmentCount: {}",
                result.taskNo(), result.reviewOrderNo(), result.taskStatus(),
                result.processedSegmentCount(), result.totalSegmentCount());
        return result;
    }

    /**
     * 领取一个到期任务并处理下一段决策数据。
     * 任务租约绑定当前工作节点；单段失败会记录可重试或终止状态，不向调度线程传播业务异常。
     *
     * @return 成功领取到任务时返回 {@code true}，没有到期任务时返回 {@code false}
     */
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
            LOGGER.info("结算复核决策任务分段处理完成, event: SETTLEMENT_REVIEW_DECISION_CHUNK_END, taskNo: {}, decisionAction: {}",
                    task.getTaskNo(), task.getDecisionAction());
        } catch (RuntimeException exception) {
            LOGGER.warn("结算复核决策任务处理失败，准备记录补偿状态, event: SETTLEMENT_REVIEW_DECISION_CHUNK_FAILED, taskNo: {}, exceptionType: {}",
                    task.getTaskNo(), exception.getClass().getSimpleName());
            transactions.recordFailure(task.getTaskNo(), exception, maximumDuration, nowUtc());
        }
        return true;
    }

    private LocalDateTime nowUtc() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
