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
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingProcessingApplicationService
 * @date : 2026-08-26 16:20
 * @email : scott_x@163.com
 * @description : 编排清分领取、事务外准备、阶段B提交和受控失败事务；只捕获带稳定失败码的业务异常，未知技术异常向MQ传播。
 * @status : create
 */
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
     * @param nowUtc 本次处理统一 UTC 时间
     * @return 可以安全 ACK 的处理结果
     * @throws RuntimeException 租约竞争或未知技术异常，交由 RocketMQ 原生重试
     */
    public ClearingProcessingResult process(PaymentTransactionEventMessage message,
                                            String processingOwner,
                                            LocalDateTime nowUtc) {
        if (!StringUtils.hasText(processingOwner) || nowUtc == null) {
            throw new IllegalArgumentException("clearing processing owner and UTC time are required");
        }
        long startNanos = System.nanoTime();
        ClearingProcessingResult result = null;
        try {
            eventValidator.validate(message);
            ClearingClaimResult claim = persistenceService.claim(message, processingOwner, nowUtc);
            result = switch (claim.outcome()) {
                case ALREADY_CONSUMED -> ClearingProcessingResult.ALREADY_CONSUMED;
                case ALREADY_COMPLETED -> ClearingProcessingResult.ALREADY_COMPLETED;
                case RETRY_ALREADY_SCHEDULED -> ClearingProcessingResult.RETRY_ALREADY_SCHEDULED;
                case MANUAL_REVIEW_REQUIRED -> ClearingProcessingResult.MANUAL_REVIEW_ACKNOWLEDGED;
                case STALE_RETRY -> ClearingProcessingResult.STALE_RETRY_ACKNOWLEDGED;
                case BUSY -> throw new IllegalStateException("clearing processing lease is busy");
                case ACQUIRED -> completeAcquired(message, claim, processingOwner, nowUtc);
            };
            metrics.recordProcessing(result);
            metrics.recordEventConsumed(result, message.getTransactionType());
            return result;
        } catch (RuntimeException exception) {
            metrics.recordTechnicalFailure();
            throw exception;
        } finally {
            metrics.recordDuration(result == null ? "TECHNICAL_FAILURE" : result.name(),
                    System.nanoTime() - startNanos);
        }
    }

    private ClearingProcessingResult completeAcquired(PaymentTransactionEventMessage message,
                                                      ClearingClaimResult claim,
                                                      String processingOwner,
                                                      LocalDateTime nowUtc) {
        try {
            CompletionCommand command = preparationService.prepare(message, claim, processingOwner);
            var completion = completionService.complete(command, nowUtc);
            metrics.recordCompleted(claim.operation().transactionType(), claim.operation().labelCurrency(),
                    completion.clearingStatus());
            if (completion.reserveDetailCount() > 0
                    && "REFUND".equals(claim.operation().transactionType())) {
                metrics.recordReserveReturn(claim.operation().labelCurrency(), "SUCCESS");
            }
            return ClearingProcessingResult.COMPLETED;
        } catch (ClearingProcessingException failure) {
            failureService.recordFailure(message, claim, processingOwner, failure, nowUtc);
            metrics.recordFailure(failure.getFailureCode());
            return ClearingProcessingResult.CONTROLLED_FAILURE_RECORDED;
        }
    }
}
