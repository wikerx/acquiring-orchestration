package com.scott.payment.clearing.application;

import com.scott.payment.clearing.domain.model.ClearingCompletionModels.CompletionCommand;
import com.scott.payment.clearing.domain.service.ClearingEventValidator;
import com.scott.payment.clearing.dto.ClearingClaimResult;
import com.scott.payment.clearing.exception.ClearingProcessingException;
import com.scott.payment.clearing.service.ClearingCompletionService;
import com.scott.payment.clearing.service.ClearingFailureService;
import com.scott.payment.clearing.service.ClearingPersistenceService;
import com.scott.payment.clearing.service.ClearingPreparationService;
import com.scott.payment.clearing.support.ClearingOperationalMetrics;
import com.scott.payment.component.mq.message.PaymentTransactionEventMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * 清分消息应用编排服务。
 *
 * <p>依次执行事件校验、Stage A 租约领取、事务外准备和 Stage B 原子提交。只有已经完成或已经持久化为
 * 受控失败的结果可以安全 ACK；租约竞争和未知技术异常必须继续抛给 RocketMQ 重试。</p>
 */
@Slf4j
@Service
public class ClearingProcessingApplicationService {

    private final ClearingEventValidator eventValidator;
    private final ClearingPersistenceService persistenceService;
    private final ClearingPreparationService preparationService;
    private final ClearingCompletionService completionService;
    private final ClearingFailureService failureService;
    private final ClearingOperationalMetrics metrics;

    /**
     * 创建清分应用编排服务。
     *
     * @param eventValidator 清分消息契约校验器
     * @param persistenceService 阶段 A 租约服务
     * @param preparationService 事务外准备服务
     * @param completionService 阶段 B 原子提交服务
     * @param failureService 受控失败独立事务服务
     */
    public ClearingProcessingApplicationService(ClearingEventValidator eventValidator,
                                                ClearingPersistenceService persistenceService,
                                                ClearingPreparationService preparationService,
                                                ClearingCompletionService completionService,
                                                ClearingFailureService failureService,
                                                ClearingOperationalMetrics metrics) {
        this.eventValidator = eventValidator;
        this.persistenceService = persistenceService;
        this.preparationService = preparationService;
        this.completionService = completionService;
        this.failureService = failureService;
        this.metrics = metrics;
    }

    /**
     * 处理单条交易终态或清分重试消息。
     *
     * @param message 已反序列化的非敏感清分输入
     * @param processingOwner 本次处理唯一租约标识
     * @param now 本次处理统一平台本地时间
     * @return 可以安全 ACK 的处理结果
     * @throws RuntimeException 租约竞争或未知技术异常，交由 RocketMQ 原生重试
     */
    public ClearingProcessingResult process(PaymentTransactionEventMessage message,
                                            String processingOwner,
                                            LocalDateTime now) {
        if (!StringUtils.hasText(processingOwner) || now == null) {
            throw new IllegalArgumentException("clearing processing owner and platform time are required");
        }
        long startNanos = System.nanoTime();
        ClearingProcessingResult result = null;
        try {
            eventValidator.validate(message);
            ClearingClaimResult claim = persistenceService.claim(message, processingOwner, now);
            result = switch (claim.outcome()) {
                case ALREADY_CONSUMED -> ClearingProcessingResult.ALREADY_CONSUMED;
                case ALREADY_COMPLETED -> ClearingProcessingResult.ALREADY_COMPLETED;
                case RETRY_ALREADY_SCHEDULED -> ClearingProcessingResult.RETRY_ALREADY_SCHEDULED;
                case MANUAL_REVIEW_REQUIRED -> ClearingProcessingResult.MANUAL_REVIEW_ACKNOWLEDGED;
                case STALE_RETRY -> ClearingProcessingResult.STALE_RETRY_ACKNOWLEDGED;
                case BUSY -> throw new ClearingLeaseBusyException();
                case ACQUIRED -> completeAcquired(message, claim, processingOwner, now);
            };
            metrics.recordProcessing(result);
            metrics.recordEventConsumed(result, message.getTransactionType());
            return result;
        } catch (ClearingLeaseBusyException exception) {
            metrics.recordTechnicalFailure();
            log.warn("清分处理租约被其他实例持有并交由MQ重试, event: CLEARING_PROCESSING_LEASE_BUSY, transactionId: {}, operationId: {}",
                    message == null ? null : message.getTransactionId(),
                    message == null ? null : message.getOperationId());
            throw exception;
        } catch (RuntimeException exception) {
            metrics.recordTechnicalFailure();
            log.error("清分消息处理发生技术异常并交由MQ重试, event: CLEARING_PROCESSING_TECHNICAL_FAILURE, transactionId: {}, operationId: {}, exceptionType: {}",
                    message == null ? null : message.getTransactionId(),
                    message == null ? null : message.getOperationId(),
                    exception.getClass().getSimpleName(), exception);
            throw exception;
        } finally {
            metrics.recordDuration(result == null ? "TECHNICAL_FAILURE" : result.name(),
                    System.nanoTime() - startNanos);
        }
    }

    /**
     * 仅对 Stage A 成功领取租约的消息执行事务外准备和 Stage B 原子提交。
     */
    private ClearingProcessingResult completeAcquired(PaymentTransactionEventMessage message,
                                                      ClearingClaimResult claim,
                                                      String processingOwner,
                                                      LocalDateTime now) {
        try {
            CompletionCommand command = preparationService.prepare(message, claim, processingOwner);
            var completion = completionService.complete(command, now);
            metrics.recordCompleted(claim.operation().transactionType(), claim.operation().labelCurrency(),
                    completion.clearingStatus());
            if (completion.reserveDetailCount() > 0
                    && "REFUND".equals(claim.operation().transactionType())) {
                metrics.recordReserveReturn(claim.operation().labelCurrency(), "SUCCESS");
            }
            return ClearingProcessingResult.COMPLETED;
        } catch (ClearingProcessingException failure) {
            failureService.recordFailure(message, claim, processingOwner, failure, now);
            metrics.recordFailure(failure.getFailureCode());
            log.warn("清分受控失败已持久化, event: CLEARING_CONTROLLED_FAILURE_RECORDED, transactionId: {}, operationId: {}, failureCode: {}",
                    message.getTransactionId(), message.getOperationId(), failure.getFailureCode());
            return ClearingProcessingResult.CONTROLLED_FAILURE_RECORDED;
        }
    }

    /**
     * 可恢复的租约竞争；保持 IllegalStateException 契约并与未知技术故障区分日志级别。
     */
    private static final class ClearingLeaseBusyException extends IllegalStateException {

        /**
         * 创建用于区分可恢复租约竞争的内部异常。
         */
        private ClearingLeaseBusyException() {
            super("clearing processing lease is busy");
        }
    }
}
