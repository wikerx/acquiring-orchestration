package com.scott.payment.settlement.config;

import com.scott.payment.settlement.application.SettlementAutomaticProcessingApplicationService;
import com.scott.payment.settlement.application.SettlementProjectionApplicationService;
import com.scott.payment.settlement.exception.SettlementProjectionProcessingException;
import com.scott.payment.settlement.service.SettlementEventPublisherService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementAutomaticScheduler
 * @date : 2026-08-26 23:50
 * @email : scott_x@163.com
 * @description : 服务启动即生效的结算调度入口；无 yml/Nacos 业务开关，跨实例排他完全由数据库状态和租约保证。
 * @status : create
 */
@Component
public class SettlementAutomaticScheduler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SettlementAutomaticScheduler.class);
    /**
     * {@code MAX_BATCHES_PER_TICK}常量，统一 {@code SettlementAutomaticScheduler} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int MAX_BATCHES_PER_TICK = 10;

    private final SettlementAutomaticProcessingApplicationService applicationService;
    private final SettlementProjectionApplicationService projectionService;
    private final SettlementEventPublisherService eventPublisherService;

    public SettlementAutomaticScheduler(SettlementAutomaticProcessingApplicationService applicationService,
                                        SettlementProjectionApplicationService projectionService,
                                        SettlementEventPublisherService eventPublisherService) {
        this.applicationService = applicationService;
        this.projectionService = projectionService;
        this.eventPublisherService = eventPublisherService;
    }

    /** 每 30 秒有界激活候选并尝试创建成熟日批。 */
    @Scheduled(initialDelay = 5_000L, fixedDelay = 30_000L)
    public void prepareBatches() {
        try {
            applicationService.prepare();
        } catch (RuntimeException exception) {
            LOGGER.error("Automatic settlement batch preparation failed", exception);
        }
    }

    /** 每秒最多处理十个可租用批次，空队列立即结束本轮。 */
    @Scheduled(initialDelay = 10_000L, fixedDelay = 1_000L)
    public void processBatches() {
        for (int index = 0; index < MAX_BATCHES_PER_TICK; index++) {
            try {
                if (!applicationService.processNext()) {
                    return;
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Automatic settlement batch processing failed", exception);
                return;
            }
        }
    }

    /** 每秒有界推进交易状态投影；主事务失败后以独立事务记录次数和指数退避。 */
    @Scheduled(initialDelay = 12_000L, fixedDelay = 1_000L)
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
                LOGGER.error("Automatic settlement transaction projection failed", exception);
                return;
            }
        }
    }

    /** 每秒有界发布结算完成 FIFO Outbox，MQ 异常不阻塞批次资金处理。 */
    @Scheduled(initialDelay = 15_000L, fixedDelay = 1_000L)
    public void publishEvents() {
        for (int index = 0; index < 50; index++) {
            try {
                if (!eventPublisherService.publishNext(java.time.LocalDateTime.now())) {
                    return;
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Automatic settlement event publication failed", exception);
                return;
            }
        }
    }

    /** 投影主事务已经回滚，此处只记录不含交易敏感正文的任务号和稳定失败码。 */
    private void recordProjectionFailure(SettlementProjectionProcessingException exception) {
        try {
            boolean recorded = projectionService.recordFailure(exception, java.time.LocalDateTime.now());
            if (!recorded) {
                LOGGER.warn("Settlement projection failure state CAS skipped, taskNo={}, failureCode={}",
                        exception.getTaskNo(), exception.getFailureCode());
            }
        } catch (RuntimeException recordException) {
            LOGGER.error("Settlement projection failure state persistence failed, taskNo={}",
                    exception.getTaskNo(), recordException);
        }
    }
}
