package com.scott.payment.risk.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.redis.idempotent.IdempotentAcquireResult;
import com.scott.payment.component.redis.idempotent.IdempotentService;
import com.scott.payment.risk.config.RiskEvaluationProperties;
import com.scott.payment.risk.mq.message.RiskEvaluationAuditMessage;
import com.scott.payment.risk.repository.RiskAuditRecordWriter;
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
 * @date : 2026-07-28 10:00
 * @email : scott_x@163.com
 * @description : 消费风控评估审计消息；Redis 负责快速去重，risk_record_no 唯一键承担最终幂等
 * @status : create
 *
 * <p>Redis 降级时必须继续写审计主库；只有取得 Redis 占用且数据库事务失败时才释放，保证 MQ 重投可恢复。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "risk.evaluation", name = "audit-mq-enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = MqTopic.RISK_EVALUATION_AUDIT,
        consumerGroup = RiskMqConstants.RISK_EVALUATION_AUDIT_CONSUMER_GROUP,
        selectorExpression = RiskMqConstants.RISK_EVALUATION_AUDIT_TAG,
        messageModel = MessageModel.CLUSTERING
)
public class RiskEvaluationAuditConsumer implements RocketMQListener<String> {

    /**
     * 风控审计 MQ 辅助去重命名空间。
     */
    private static final String IDEMPOTENT_NAMESPACE = "risk-audit";

    /**
     * 风控审计主库事务写入器，以 riskRecordNo 唯一键承担最终幂等。
     */
    private final RiskAuditRecordWriter riskAuditRecordWriter;

    /**
     * Redis MQ 辅助去重服务；不可用时显式回退数据库唯一约束。
     */
    private final IdempotentService idempotentService;

    /**
     * 风控审计消费去重 TTL 等运行配置。
     */
    private final RiskEvaluationProperties properties;

    /**
     * 创建风控评估审计消费者。
     *
     * @param riskAuditRecordWriter 风控审计事务写入器
     * @param idempotentService     Redis 辅助幂等服务
     * @param properties            风控评估配置，提供 MQ 去重 TTL
     */
    public RiskEvaluationAuditConsumer(RiskAuditRecordWriter riskAuditRecordWriter,
                                       IdempotentService idempotentService,
                                       RiskEvaluationProperties properties) {
        this.riskAuditRecordWriter = riskAuditRecordWriter;
        this.idempotentService = idempotentService;
        this.properties = properties;
    }

    /**
     * 消费并持久化一条风控评估审计消息。
     *
     * @param payload 风控审计消息 JSON；日志只输出业务追踪字段，不输出完整命中值
     */
    @Override
    public void onMessage(String payload) {
        long startNanos = System.nanoTime();
        RiskEvaluationAuditMessage message = JsonUtils.parseObject(payload, RiskEvaluationAuditMessage.class);
        if (message == null || !StringUtils.hasText(message.getRiskRecordNo())) {
            log.warn("event: RISK_AUDIT_CONSUME_SKIP stage=MQ_CONSUME traceId: {} reason=messageInvalid payloadLength: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    payload == null ? 0 : payload.length(),
                    elapsedMillis(startNanos));
            return;
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            String idempotentKey = message.idempotentKey();
            IdempotentAcquireResult acquireResult = idempotentService.acquireMq(
                    IDEMPOTENT_NAMESPACE,
                    idempotentKey,
                    properties.getAuditConsumeIdempotentTtlSeconds()
            );
            if (acquireResult == IdempotentAcquireResult.DUPLICATE) {
                log.info("event: RISK_AUDIT_DUPLICATE stage=MQ_CONSUME traceId: {} riskRecordNo: {} messageId: {} durationMs: {}",
                        TraceContext.getTraceId(),
                        message.getRiskRecordNo(),
                        message.getMessageId(),
                        elapsedMillis(startNanos));
                return;
            }
            if (acquireResult == IdempotentAcquireResult.FALLBACK) {
                log.warn("event: RISK_AUDIT_IDEMPOTENT_FALLBACK stage=MQ_CONSUME traceId: {} "
                                + "riskRecordNo: {} messageId: {} action: continueToDatabaseUniqueConstraint",
                        TraceContext.getTraceId(),
                        message.getRiskRecordNo(),
                        message.getMessageId());
            }
            try {
                riskAuditRecordWriter.write(message);
                log.info("event: RISK_AUDIT_CONSUMED stage=MQ_CONSUME traceId: {} riskRecordNo: {} merchantId: {} merchantOrderNo: {} decision: {} hitCount: {} durationMs: {}",
                        TraceContext.getTraceId(),
                        message.getRiskRecordNo(),
                        message.getMerchantId(),
                        message.getMerchantOrderNo(),
                        message.getDecisionResult(),
                        message.getHitCount(),
                        elapsedMillis(startNanos));
            } catch (RuntimeException exception) {
                releaseForRetry(idempotentKey, acquireResult, message, exception);
                throw exception;
            }
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 数据库事务失败后释放本次实际取得的 Redis 占用。
     *
     * @param idempotentKey     风控记录业务幂等键
     * @param acquireResult     本次 Redis 获取结果
     * @param message           风控审计消息，仅用于记录号和消息号追踪
     * @param originalException 原始数据库异常，释放异常会作为 suppressed 附加
     */
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
                    properties.getAuditConsumeIdempotentTtlSeconds()
            );
        } catch (RuntimeException releaseException) {
            originalException.addSuppressed(releaseException);
            log.error("event: RISK_AUDIT_IDEMPOTENT_RELEASE_FAILED stage=MQ_CONSUME traceId: {} riskRecordNo: {} messageId: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    message.getRiskRecordNo(),
                    message.getMessageId(),
                    0,
                    releaseException);
        }
    }

    /**
     * 计算单条风控审计消息的消费耗时。
     *
     * @param startNanos System.nanoTime 起始值
     * @return 耗时，单位毫秒
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
