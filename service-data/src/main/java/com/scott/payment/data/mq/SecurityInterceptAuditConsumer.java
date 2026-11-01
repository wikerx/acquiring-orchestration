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
 * service-data 安全拦截审计消费者，Redis 只做辅助去重，event_no 数据库唯一键承担最终幂等。
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

    /**
     * Redis MQ 辅助幂等命名空间。
     */
    private static final String IDEMPOTENT_NAMESPACE = "security-audit";

    /**
     * 安全拦截审计写入服务。
     */
    private final SecurityInterceptAuditPersistenceService persistenceService;

    /**
     * Redis MQ 辅助幂等服务。
     */
    private final IdempotentService idempotentService;

    /**
     * 安全审计 MQ 配置。
     */
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
            log.error("安全拦截审计消息缺少必填字段, event: DATA_SECURITY_AUDIT_INVALID, traceId: {}, payloadLength: {}",
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
                    log.info("安全拦截审计消息幂等命中, event: DATA_SECURITY_AUDIT_DUPLICATE, traceId: {}, eventNo: {}, messageId: {}, durationMs: {}",
                            TraceContext.getTraceId(), message.getEventNo(), message.getMessageId(), elapsedMillis(startNanos));
                    return;
                }
                log.warn("安全审计 Redis 占用与数据库事实不一致, event: DATA_SECURITY_AUDIT_STALE_REDIS_CLAIM, traceId: {}, eventNo: {}, messageId: {}, action: continueToDatabaseUniqueConstraint",
                        TraceContext.getTraceId(), message.getEventNo(), message.getMessageId());
            }
            if (acquireResult == IdempotentAcquireResult.FALLBACK) {
                log.warn("安全审计 Redis 幂等服务降级, event: DATA_SECURITY_AUDIT_IDEMPOTENT_FALLBACK, traceId: {}, eventNo: {}, messageId: {}, action: continueToDatabaseUniqueConstraint",
                        TraceContext.getTraceId(), message.getEventNo(), message.getMessageId());
            }
            try {
                persistenceService.persist(message);
            } catch (RuntimeException exception) {
                releaseForRetry(idempotentKey, acquireResult, message, exception);
                throw exception;
            }
            log.info("安全拦截审计消息消费完成, event: DATA_SECURITY_AUDIT_CONSUMED, traceId: {}, eventNo: {}, eventType: {}, merchantId: {}, riskLevel: {}, durationMs: {}",
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

    /**
     * 解析安全审计消息，畸形载荷只记录长度和异常类型。
     *
     * @param payload RocketMQ JSON 消息体
     * @return 安全拦截审计消息
     */
    private SecurityInterceptAuditMessage parseMessage(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("security audit payload is empty");
        }
        try {
            return JsonUtils.parseObject(payload, SecurityInterceptAuditMessage.class);
        } catch (RuntimeException exception) {
            log.error("安全拦截审计消息解析失败, event: DATA_SECURITY_AUDIT_DESERIALIZE_FAILED, payloadLength: {}, exceptionType: {}",
                    payload.length(), exception.getClass().getSimpleName());
            throw new IllegalArgumentException("security audit payload is invalid", exception);
        }
    }

    /**
     * 校验数据库幂等键和最小事件语义字段。
     *
     * @param message 已解析安全拦截审计消息
     * @return true 表示消息具备可持久化的稳定事件身份
     */
    private boolean isValid(SecurityInterceptAuditMessage message) {
        return message != null
                && StringUtils.hasText(message.getEventNo())
                && StringUtils.hasText(message.getEventType())
                && StringUtils.hasText(message.getSourceLayer())
                && StringUtils.hasText(message.getAction());
    }

    /**
     * 数据库失败时只释放当前消费者实际取得的 Redis 辅助占用。
     *
     * @param idempotentKey 安全事件数据库幂等键
     * @param acquireResult 当前消费者的 Redis 辅助占用结果
     * @param message 已解析安全拦截审计消息
     * @param originalException 数据库处理异常
     */
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
            log.error("安全审计 Redis 幂等占用释放失败, event: DATA_SECURITY_AUDIT_IDEMPOTENT_RELEASE_FAILED, traceId: {}, eventNo: {}, messageId: {}",
                    TraceContext.getTraceId(), message.getEventNo(), message.getMessageId(), releaseException);
        }
    }

    /**
     * 计算单条消息消费耗时，单位毫秒。
     *
     * @param startNanos 单调时钟起点
     * @return 耗时毫秒数
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
