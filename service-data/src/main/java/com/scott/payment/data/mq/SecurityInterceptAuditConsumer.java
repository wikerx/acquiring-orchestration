package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.SecurityInterceptAuditMessage;
import com.scott.payment.component.mq.properties.SecurityAuditMqProperties;
import com.scott.payment.component.redis.idempotent.IdempotentAcquireResult;
import com.scott.payment.component.redis.idempotent.IdempotentService;
import com.scott.payment.data.service.SecurityInterceptAuditPersistenceService;
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
 * @classname : SecurityInterceptAuditConsumer
 * @date : 2026-08-01 18:00
 * @email : scott_x@163.com
 * @description : service-data 安全拦截审计消费者，Redis 只做辅助去重，event_no 数据库唯一键承担最终幂等
 * @status : create
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "acquiring.security-audit.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = MqTopic.SECURITY_INTERCEPT_AUDIT,
        consumerGroup = DataMqConsumerGroups.SECURITY_INTERCEPT_AUDIT,
        selectorExpression = MqTag.SECURITY_INTERCEPT_AUDIT,
        messageModel = MessageModel.CLUSTERING
)
public class SecurityInterceptAuditConsumer implements RocketMQListener<String> {

    /** Redis MQ 辅助幂等命名空间。 */
    private static final String IDEMPOTENT_NAMESPACE = "security-audit";

    /** 安全拦截审计写入服务。 */
    private final SecurityInterceptAuditPersistenceService persistenceService;

    /** Redis MQ 辅助幂等服务。 */
    private final IdempotentService idempotentService;

    /** 安全审计 MQ 配置。 */
    private final SecurityAuditMqProperties properties;

    /**
     * 创建安全拦截审计消费者。
     *
     * @param persistenceService 安全拦截审计写入服务
     * @param idempotentService Redis MQ 辅助幂等服务
     * @param properties 安全审计 MQ 配置
     */
    public SecurityInterceptAuditConsumer(SecurityInterceptAuditPersistenceService persistenceService,
                                          IdempotentService idempotentService,
                                          SecurityAuditMqProperties properties) {
        this.persistenceService = persistenceService;
        this.idempotentService = idempotentService;
        this.properties = properties;
    }

    /**
     * 消费并幂等持久化一条已脱敏安全拦截审计消息。
     *
     * @param payload RocketMQ JSON 消息体；日志中禁止输出原文
     */
    @Override
    public void onMessage(String payload) {
        long startNanos = System.nanoTime();
        SecurityInterceptAuditMessage message = parseMessage(payload);
        if (!isValid(message)) {
            log.error("event: DATA_SECURITY_AUDIT_INVALID traceId: {} payloadLength: {}",
                    TraceContext.getTraceId(), payload == null ? 0 : payload.length());
            throw new IllegalArgumentException("security audit message required fields are missing");
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
                if (persistenceService.existsByEventNo(message.getEventNo())) {
                    log.info("event: DATA_SECURITY_AUDIT_DUPLICATE traceId: {} eventNo: {} messageId: {} durationMs: {}",
                            TraceContext.getTraceId(), message.getEventNo(), message.getMessageId(), elapsedMillis(startNanos));
                    return;
                }
                log.warn("event: DATA_SECURITY_AUDIT_STALE_REDIS_CLAIM traceId: {} eventNo: {} messageId: {} action: continueToDatabaseUniqueConstraint",
                        TraceContext.getTraceId(), message.getEventNo(), message.getMessageId());
            }
            if (acquireResult == IdempotentAcquireResult.FALLBACK) {
                log.warn("event: DATA_SECURITY_AUDIT_IDEMPOTENT_FALLBACK traceId: {} eventNo: {} messageId: {} action: continueToDatabaseUniqueConstraint",
                        TraceContext.getTraceId(), message.getEventNo(), message.getMessageId());
            }
            try {
                persistenceService.persist(message);
            } catch (RuntimeException exception) {
                releaseForRetry(idempotentKey, acquireResult, message, exception);
                throw exception;
            }
            log.info("event: DATA_SECURITY_AUDIT_CONSUMED traceId: {} eventNo: {} eventType: {} merchantId: {} riskLevel: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    message.getEventNo(),
                    message.getEventType(),
                    message.getMerchantId(),
                    message.getRiskLevel(),
                    elapsedMillis(startNanos));
        } finally {
            TraceContext.clear();
        }
    }

    /** 解析安全审计消息，畸形载荷只记录长度和异常类型。 */
    private SecurityInterceptAuditMessage parseMessage(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("security audit payload is empty");
        }
        try {
            return JsonUtils.parseObject(payload, SecurityInterceptAuditMessage.class);
        } catch (RuntimeException exception) {
            log.error("event: DATA_SECURITY_AUDIT_DESERIALIZE_FAILED payloadLength: {} exceptionType: {}",
                    payload.length(), exception.getClass().getSimpleName());
            throw new IllegalArgumentException("security audit payload is invalid", exception);
        }
    }

    /** 校验数据库幂等键和最小事件语义字段。 */
    private boolean isValid(SecurityInterceptAuditMessage message) {
        return message != null
                && StringUtils.hasText(message.getEventNo())
                && StringUtils.hasText(message.getEventType())
                && StringUtils.hasText(message.getSourceLayer())
                && StringUtils.hasText(message.getAction());
    }

    /** 数据库失败时只释放当前消费者实际取得的 Redis 辅助占用。 */
    private void releaseForRetry(String idempotentKey,
                                 IdempotentAcquireResult acquireResult,
                                 SecurityInterceptAuditMessage message,
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
            log.error("event: DATA_SECURITY_AUDIT_IDEMPOTENT_RELEASE_FAILED traceId: {} eventNo: {} messageId: {}",
                    TraceContext.getTraceId(), message.getEventNo(), message.getMessageId(), releaseException);
        }
    }

    /** 计算单条消息消费耗时，单位毫秒。 */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
