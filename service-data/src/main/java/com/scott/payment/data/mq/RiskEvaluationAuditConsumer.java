package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.RiskEvaluationAuditMessage;
import com.scott.payment.component.mq.properties.RiskAuditMqProperties;
import com.scott.payment.component.redis.idempotent.IdempotentAcquireResult;
import com.scott.payment.component.redis.idempotent.IdempotentService;
import com.scott.payment.data.service.RiskAuditPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskEvaluationAuditConsumer
 * @date : 2026-08-01 14:50
 * @email : scott_x@163.com
 * @description : service-data 风控评估审计消费者，Redis 辅助去重且数据库唯一键承担最终幂等
 * @status : create
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "acquiring.risk-audit.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = MqTopic.RISK_EVALUATION_AUDIT,
        consumerGroup = DataMqConsumerGroups.RISK_EVALUATION_AUDIT,
        selectorExpression = MqTag.RISK_EVALUATION_AUDIT,
        messageModel = MessageModel.CLUSTERING
)
public class RiskEvaluationAuditConsumer implements RocketMQListener<String> {

    /** Redis MQ 辅助幂等命名空间。 */
    private static final String IDEMPOTENT_NAMESPACE = "risk-audit";

    /** 风控审计事务写入服务。 */
    private final RiskAuditPersistenceService persistenceService;

    /** Redis MQ 辅助幂等服务。 */
    private final IdempotentService idempotentService;

    /** 风控审计消费配置。 */
    private final RiskAuditMqProperties properties;

    /**
     * 创建风控评估审计消费者。
     *
     * @param persistenceService 风控审计事务写入服务
     * @param idempotentService  Redis MQ 辅助幂等服务
     * @param properties         风控审计消费配置
     */
    public RiskEvaluationAuditConsumer(RiskAuditPersistenceService persistenceService,
                                       IdempotentService idempotentService,
                                       RiskAuditMqProperties properties) {
        this.persistenceService = persistenceService;
        this.idempotentService = idempotentService;
        this.properties = properties;
    }

    /**
     * 消费并持久化一条已脱敏风控评估审计消息。
     *
     * @param payload RocketMQ JSON 消息体
     */
    @Override
    public void onMessage(String payload) {
        long startNanos = System.nanoTime();
        RiskEvaluationAuditMessage message = parseMessage(payload);
        if (message == null || !StringUtils.hasText(message.getRiskRecordNo())) {
            log.error("event: DATA_RISK_AUDIT_INVALID traceId: {} payloadLength: {}",
                    TraceContext.getTraceId(), payload == null ? 0 : payload.length());
            throw new IllegalArgumentException("risk audit message required fields are missing");
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            String idempotentKey = message.idempotentKey().trim();
            IdempotentAcquireResult acquireResult = idempotentService.acquireMq(
                    IDEMPOTENT_NAMESPACE,
                    idempotentKey,
                    properties.getConsumeIdempotentTtlSeconds()
            );
            if (acquireResult == IdempotentAcquireResult.DUPLICATE) {
                if (persistenceService.existsByRiskRecordNo(message.getRiskRecordNo())) {
                    log.info("event: DATA_RISK_AUDIT_DUPLICATE traceId: {} riskRecordNo: {} messageId: {} durationMs: {}",
                            TraceContext.getTraceId(), message.getRiskRecordNo(), message.getMessageId(), elapsedMillis(startNanos));
                    return;
                }
                log.warn("event: DATA_RISK_AUDIT_STALE_REDIS_CLAIM traceId: {} riskRecordNo: {} messageId: {} action: continueToDatabaseUniqueConstraint",
                        TraceContext.getTraceId(), message.getRiskRecordNo(), message.getMessageId());
            }
            if (acquireResult == IdempotentAcquireResult.FALLBACK) {
                log.warn("event: DATA_RISK_AUDIT_IDEMPOTENT_FALLBACK traceId: {} riskRecordNo: {} messageId: {} action: continueToDatabaseUniqueConstraint",
                        TraceContext.getTraceId(), message.getRiskRecordNo(), message.getMessageId());
            }
            try {
                persistenceService.persist(message);
            } catch (RuntimeException exception) {
                releaseForRetry(idempotentKey, acquireResult, message, exception);
                throw exception;
            }
            log.info("event: DATA_RISK_AUDIT_CONSUMED traceId: {} riskRecordNo: {} merchantId: {} paymentOrderNo: {} decision: {} hitCount: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    message.getRiskRecordNo(),
                    message.getMerchantId(),
                    message.getPaymentOrderNo(),
                    message.getDecisionResult(),
                    message.getHitCount(),
                    elapsedMillis(startNanos));
        } finally {
            TraceContext.clear();
        }
    }

    /** 解析风控审计消息，畸形载荷不输出原文。 */
    private RiskEvaluationAuditMessage parseMessage(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("risk audit payload is empty");
        }
        try {
            return JsonUtils.parseObject(payload, RiskEvaluationAuditMessage.class);
        } catch (RuntimeException exception) {
            log.error("event: DATA_RISK_AUDIT_DESERIALIZE_FAILED payloadLength: {} exceptionType: {}",
                    payload.length(), exception.getClass().getSimpleName());
            throw new IllegalArgumentException("risk audit payload is invalid", exception);
        }
    }

    /** 数据库失败时释放当前消费者实际取得的 Redis 辅助占用。 */
    private void releaseForRetry(String idempotentKey,
                                 IdempotentAcquireResult acquireResult,
                                 RiskEvaluationAuditMessage message,
                                 RuntimeException originalException) {
        if (acquireResult != IdempotentAcquireResult.ACQUIRED) {
            return;
        }
        try {
            idempotentService.releaseMq(
                    IDEMPOTENT_NAMESPACE,
                    idempotentKey,
                    properties.getConsumeIdempotentTtlSeconds()
            );
        } catch (RuntimeException releaseException) {
            originalException.addSuppressed(releaseException);
            log.error("event: DATA_RISK_AUDIT_IDEMPOTENT_RELEASE_FAILED traceId: {} riskRecordNo: {} messageId: {}",
                    TraceContext.getTraceId(), message.getRiskRecordNo(), message.getMessageId(), releaseException);
        }
    }

    /** 计算单条消息消费耗时。 */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
