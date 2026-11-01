package com.scott.payment.settlement.config;

import com.scott.payment.settlement.application.SettlementAutomaticProcessingApplicationService;
import com.scott.payment.settlement.application.SettlementProjectionApplicationService;
import com.scott.payment.settlement.application.SettlementManualReviewApplicationService;
import com.scott.payment.settlement.application.SettlementReviewDecisionApplicationService;
import com.scott.payment.settlement.exception.SettlementProjectionProcessingException;
import com.scott.payment.settlement.service.SettlementEventPublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 结算后台任务调度入口。
 *
 * <p>调度器只控制轮询频率和单轮处理上限，跨实例排他由数据库状态、版本和租约保证。</p>
 */
@Component
public class SettlementAutomaticScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SettlementAutomaticScheduler.class);
    /**
     * 单轮最多处理的批次数，避免空闲任务相互饥饿。
     */
    private static final int MAX_BATCHES_PER_TICK = 10;

    private final SettlementAutomaticProcessingApplicationService applicationService;
    private final SettlementProjectionApplicationService projectionService;
    private final SettlementEventPublisherService eventPublisherService;
    private final SettlementManualReviewApplicationService manualReviewService;
    private final SettlementReviewDecisionApplicationService reviewDecisionService;

    public SettlementAutomaticScheduler(SettlementAutomaticProcessingApplicationService applicationService,
                                        SettlementProjectionApplicationService projectionService,
                                        SettlementEventPublisherService eventPublisherService,
                                        SettlementManualReviewApplicationService manualReviewService) {
        this(applicationService, projectionService, eventPublisherService, manualReviewService, null);
    }

    @Autowired
    public SettlementAutomaticScheduler(SettlementAutomaticProcessingApplicationService applicationService,
                                        SettlementProjectionApplicationService projectionService,
                                        SettlementEventPublisherService eventPublisherService,
                                        SettlementManualReviewApplicationService manualReviewService,
                                        SettlementReviewDecisionApplicationService reviewDecisionService) {
        this.applicationService = applicationService;
        this.projectionService = projectionService;
        this.eventPublisherService = eventPublisherService;
        this.manualReviewService = manualReviewService;
        this.reviewDecisionService = reviewDecisionService;
    }

    /**
     * 按配置的固定延迟有界激活候选并尝试创建成熟日批；该参数只调节轮询频率，不关闭自动结算。
     */
    @Scheduled(initialDelay = 5_000L,
            fixedDelayString = "${settlement.automatic-scheduler.prepare-fixed-delay-ms:30000}")
    public void prepareBatches() {
        try {
            applicationService.prepare();
        } catch (RuntimeException exception) {
            LOGGER.error("自动结算批次准备失败, event: SETTLEMENT_SCHEDULER_PREPARE_FAILED, exceptionType: {}",
                    exception.getClass().getSimpleName(), exception);
        }
    }

    /**
     * 按工作循环周期最多处理十个可租用批次，空队列立即结束本轮。
     */
    @Scheduled(initialDelay = 10_000L,
            fixedDelayString = "${settlement.automatic-scheduler.worker-fixed-delay-ms:1000}")
    public void processBatches() {
        for (int index = 0; index < MAX_BATCHES_PER_TICK; index++) {
            try {
                if (!applicationService.processNext()) {
                    return;
                }
            } catch (RuntimeException exception) {
                LOGGER.error("自动结算批次调度失败, event: SETTLEMENT_SCHEDULER_BATCH_FAILED, exceptionType: {}",
                        exception.getClass().getSimpleName(), exception);
                return;
            }
        }
    }

    /**
     * 按工作循环周期有界推进手动交易结算预审，单次数据库事务只处理一个固定分段。
     */
    @Scheduled(initialDelay = 11_000L,
            fixedDelayString = "${settlement.automatic-scheduler.worker-fixed-delay-ms:1000}")
    public void processManualReviews() {
        for (int index = 0; index < 10; index++) {
            try {
                if (!manualReviewService.processNext()) {
                    return;
                }
            } catch (RuntimeException exception) {
                LOGGER.error("手动结算预审任务调度失败, event: SETTLEMENT_SCHEDULER_MANUAL_REVIEW_FAILED, exceptionType: {}",
                        exception.getClass().getSimpleName(), exception);
                return;
            }
        }
    }

    /**
     * 按工作循环周期有界推进预审单审批、驳回或取消，每次只处理一个分段。
     */
    @Scheduled(initialDelay = 11_500L,
            fixedDelayString = "${settlement.automatic-scheduler.worker-fixed-delay-ms:1000}")
    public void processReviewDecisions() {
        if (reviewDecisionService == null) {
            return;
        }
        for (int index = 0; index < 10; index++) {
            try {
                if (!reviewDecisionService.processNext()) {
                    return;
                }
            } catch (RuntimeException exception) {
                LOGGER.error("结算复核决策任务调度失败, event: SETTLEMENT_SCHEDULER_REVIEW_DECISION_FAILED, exceptionType: {}",
                        exception.getClass().getSimpleName(), exception);
                return;
            }
        }
    }

    /**
     * 按工作循环周期有界推进交易状态投影；主事务失败后以独立事务记录次数和指数退避。
     */
    @Scheduled(initialDelay = 12_000L,
            fixedDelayString = "${settlement.automatic-scheduler.worker-fixed-delay-ms:1000}")
    public void projectTransactions() {
        for (int index = 0; index < 50; index++) {
            try {
                if (!projectionService.processNext(java.time.LocalDateTime.now())) {
                    return;
                }
            } catch (SettlementProjectionProcessingException exception) {
                recordProjectionFailure(exception);
                return;
            } catch (RuntimeException exception) {
                LOGGER.error("结算交易投影调度失败, event: SETTLEMENT_SCHEDULER_PROJECTION_FAILED, exceptionType: {}",
                        exception.getClass().getSimpleName(), exception);
                return;
            }
        }
    }

    /**
     * 按工作循环周期有界发布结算完成 FIFO Outbox，MQ 异常不阻塞批次资金处理。
     */
    @Scheduled(initialDelay = 15_000L,
            fixedDelayString = "${settlement.automatic-scheduler.worker-fixed-delay-ms:1000}")
    public void publishEvents() {
        for (int index = 0; index < 50; index++) {
            try {
                if (!eventPublisherService.publishNext(java.time.LocalDateTime.now())) {
                    return;
                }
            } catch (RuntimeException exception) {
                LOGGER.error("结算事件发布调度失败, event: SETTLEMENT_SCHEDULER_EVENT_PUBLISH_FAILED, exceptionType: {}",
                        exception.getClass().getSimpleName(), exception);
                return;
            }
        }
    }

    /**
     * 投影主事务已经回滚，此处只记录不含交易敏感正文的任务号和稳定失败码。
     */
    private void recordProjectionFailure(SettlementProjectionProcessingException exception) {
        try {
            boolean recorded = projectionService.recordFailure(exception, java.time.LocalDateTime.now());
            if (!recorded) {
                LOGGER.warn("结算投影失败状态更新被并发推进, event: SETTLEMENT_PROJECTION_FAILURE_CAS_SKIPPED, taskNo: {}, failureCode: {}",
                        exception.getTaskNo(), exception.getFailureCode());
            }
        } catch (RuntimeException recordException) {
            LOGGER.error("结算投影失败状态持久化失败, event: SETTLEMENT_PROJECTION_FAILURE_PERSIST_FAILED, taskNo: {}, failureCode: {}, exceptionType: {}",
                    exception.getTaskNo(), exception.getFailureCode(),
                    recordException.getClass().getSimpleName(), recordException);
        }
    }
}
