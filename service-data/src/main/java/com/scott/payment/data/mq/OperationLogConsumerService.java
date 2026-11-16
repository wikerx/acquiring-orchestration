package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.message.OperationLogMessage;
import com.scott.payment.component.mq.observability.OperationLogOperationalMetrics;
import com.scott.payment.component.mq.properties.OperationLogMqProperties;
import com.scott.payment.component.redis.idempotent.IdempotentAcquireResult;
import com.scott.payment.component.redis.idempotent.IdempotentService;
import com.scott.payment.data.service.OperationLogPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Admin 与 Merchant 操作日志共享消费编排，统一处理反序列化、辅助幂等、失败释放和追踪上下文
 */
@Slf4j
@Service
public class OperationLogConsumerService {

    /**
     * 操作日志事务写入服务。
     */
    private final OperationLogPersistenceService persistenceService;

    /**
     * Redis MQ 辅助幂等服务。
     */
    private final IdempotentService idempotentService;

    /**
     * 操作日志 MQ 公共配置。
     */
    private final OperationLogMqProperties properties;

    /**
     * 操作日志消费链路低基数指标。
     */
    private final OperationLogOperationalMetrics operationalMetrics;

    /**
     * 创建操作日志共享消费服务。
     *
     * @param persistenceService 操作日志事务写入服务
     * @param idempotentService  Redis MQ 辅助幂等服务
     * @param properties         操作日志 MQ 配置
     */
    public OperationLogConsumerService(OperationLogPersistenceService persistenceService,
                                       IdempotentService idempotentService,
                                       OperationLogMqProperties properties,
                                       OperationLogOperationalMetrics operationalMetrics) {
        this.persistenceService = persistenceService;
        this.idempotentService = idempotentService;
        this.properties = properties;
        this.operationalMetrics = operationalMetrics;
    }

    /**
     * 消费指定来源的一条操作日志消息。
     *
     * <p>Redis 不可用时继续进入数据库唯一约束，避免缓存故障造成审计丢失；业务写入失败时，
     * 只有 ACQUIRED 持有者可以释放自己的辅助幂等占用。</p>
     *
     * @param source  操作日志来源系统
     * @param payload RocketMQ JSON 消息体
     */
    public void consume(OperationLogSource source, String payload) {
        long startNanos = System.nanoTime();
        OperationLogMessage message;
        try {
            message = parseMessage(source, payload);
            validateSource(source, message);
        } catch (RuntimeException exception) {
            operationalMetrics.recordEvent(systemCode(source), "invalid");
            operationalMetrics.recordConsumeDuration(systemCode(source), "invalid",
                    System.nanoTime() - startNanos);
            throw exception;
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        String outcome = "failure";
        try {
            String idempotentKey;
            try {
                idempotentKey = resolveIdempotentKey(source, message);
            } catch (IllegalArgumentException exception) {
                outcome = "invalid";
                operationalMetrics.recordEvent(source.name(), "invalid");
                throw exception;
            }
            IdempotentAcquireResult acquireResult = idempotentService.acquireMq(
                    source.getIdempotentNamespace(),
                    idempotentKey,
                    properties.getConsumeIdempotentTtlSeconds()
            );
            if (acquireResult == IdempotentAcquireResult.DUPLICATE) {
                if (persistenceService.existsByIdempotentKey(idempotentKey)) {
                    outcome = "duplicate";
                    operationalMetrics.recordEvent(source.name(), "duplicate");
                    log.info("操作日志消息幂等命中, event: DATA_OPERATION_LOG_DUPLICATE, source: {}, traceId: {}, messageId: {}, durationMs: {}",
                            source, TraceContext.getTraceId(), message.getMessageId(), elapsedMillis(startNanos));
                    return;
                }
                log.warn("操作日志 Redis 占用与数据库事实不一致, event: DATA_OPERATION_LOG_STALE_REDIS_CLAIM, source: {}, traceId: {}, messageId: {}, action: continueToDatabaseUniqueConstraint",
                        source, TraceContext.getTraceId(), message.getMessageId());
                operationalMetrics.recordEvent(source.name(), "stale_claim");
            }
            if (acquireResult == IdempotentAcquireResult.FALLBACK) {
                log.warn("操作日志 Redis 幂等服务降级, event: DATA_OPERATION_LOG_IDEMPOTENT_FALLBACK, source: {}, traceId: {}, messageId: {}, action: continueToDatabaseUniqueConstraint",
                        source, TraceContext.getTraceId(), message.getMessageId());
                operationalMetrics.recordEvent(source.name(), "redis_fallback");
            }
            try {
                persistenceService.persist(message, idempotentKey);
            } catch (RuntimeException exception) {
                releaseForRetry(source, idempotentKey, acquireResult, message, exception);
                throw exception;
            }
            outcome = "success";
            operationalMetrics.recordEvent(source.name(), "consumed");
            log.info("操作日志消息消费完成, event: DATA_OPERATION_LOG_CONSUMED, source: {}, traceId: {}, messageId: {}, merchantId: {}, module: {}, operation: {}, status: {}, durationMs: {}",
                    source,
                    TraceContext.getTraceId(),
                    message.getMessageId(),
                    message.getMerchantId(),
                    message.getOperationModule(),
                    message.getOperationName(),
                    message.getOperationStatus(),
                    elapsedMillis(startNanos));
        } catch (RuntimeException exception) {
            if (!"invalid".equals(outcome)) {
                operationalMetrics.recordEvent(source.name(), "failed");
            }
            throw exception;
        } finally {
            operationalMetrics.recordConsumeDuration(source.name(), outcome,
                    System.nanoTime() - startNanos);
            TraceContext.clear();
        }
    }

    /**
     * 解析消息且不输出原始载荷，避免畸形消息把敏感内容带入日志。
     *
     * @param source  操作日志来源系统
     * @param payload RocketMQ JSON 消息体
     * @return 消息对象
     */
    private OperationLogMessage parseMessage(OperationLogSource source, String payload) {
        if (!StringUtils.hasText(payload)) {
            log.error("操作日志消息为空, event: DATA_OPERATION_LOG_INVALID, source: {}, reason: emptyPayload", source);
            throw new IllegalArgumentException("operation log payload is empty");
        }
        try {
            return JsonUtils.parseObject(payload, OperationLogMessage.class);
        } catch (RuntimeException exception) {
            log.error("操作日志消息解析失败, event: DATA_OPERATION_LOG_INVALID, source: {}, payloadLength: {}, exceptionType: {}",
                    source, payload.length(), exception.getClass().getSimpleName());
            throw new IllegalArgumentException("operation log payload is invalid", exception);
        }
    }

    /**
     * 解析消息幂等键；旧生产者未填业务键时使用 messageId 保持可重试兼容。
     *
     * @param message 操作日志消息
     * @return 非空幂等键
     */
    private String resolveIdempotentKey(OperationLogSource source, OperationLogMessage message) {
        if (StringUtils.hasText(message.getIdempotentKey())) {
            return normalizeIdempotentKey(source, message.getIdempotentKey().trim());
        }
        if (StringUtils.hasText(message.getMessageId())) {
            return normalizeIdempotentKey(source, source.name() + ':' + message.getMessageId().trim());
        }
        throw new IllegalArgumentException("operation log message must provide idempotentKey or messageId");
    }

    /**
     * 校验 Topic 来源和消息声明系统一致，禁止跨系统消息落入错误审计域。
     */
    private void validateSource(OperationLogSource source, OperationLogMessage message) {
        if (source == null || message == null || !source.name().equals(message.getSystemCode())) {
            log.error("操作日志消息来源不匹配, event: DATA_OPERATION_LOG_SOURCE_MISMATCH, source: {}, declaredSystem: {}",
                    source, message == null ? null : message.getSystemCode());
            throw new IllegalArgumentException("operation log source does not match declared system");
        }
    }

    /**
     * 将旧生产者可能生成的超长幂等键稳定哈希到数据库字段范围，避免永久重试毒消息。
     */
    private String normalizeIdempotentKey(OperationLogSource source, String idempotentKey) {
        if (idempotentKey.length() <= OperationLogMessage.IDEMPOTENT_KEY_MAX_LENGTH) {
            return idempotentKey;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(idempotentKey.getBytes(StandardCharsets.UTF_8));
            return source.name() + ":sha256:" + HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 安全获取固定系统标签；空来源统一收敛为 UNKNOWN。
     */
    private String systemCode(OperationLogSource source) {
        return source == null ? "UNKNOWN" : source.name();
    }

    /**
     * 写入失败时释放当前消费者实际取得的 Redis 辅助占用。
     *
     * @param source            操作日志来源
     * @param idempotentKey     消费幂等键
     * @param acquireResult     Redis 获取结果
     * @param message           操作日志消息
     * @param originalException 原始写入异常
     */
    private void releaseForRetry(OperationLogSource source,
                                 String idempotentKey,
                                 IdempotentAcquireResult acquireResult,
                                 OperationLogMessage message,
                                 RuntimeException originalException) {
        if (acquireResult != IdempotentAcquireResult.ACQUIRED) {
            return;
        }
        try {
            idempotentService.releaseMq(
                    source.getIdempotentNamespace(),
                    idempotentKey,
                    properties.getConsumeIdempotentTtlSeconds()
            );
        } catch (RuntimeException releaseException) {
            originalException.addSuppressed(releaseException);
            log.error("操作日志 Redis 幂等占用释放失败, event: DATA_OPERATION_LOG_IDEMPOTENT_RELEASE_FAILED, source: {}, traceId: {}, messageId: {}",
                    source, TraceContext.getTraceId(), message.getMessageId(), releaseException);
        }
    }

    /**
     * 计算单条消息消费耗时。
     *
     * @param startNanos 单调时钟起始值
     * @return 耗时毫秒数
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
