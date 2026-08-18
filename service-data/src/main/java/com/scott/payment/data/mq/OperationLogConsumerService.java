package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.message.OperationLogMessage;
import com.scott.payment.component.mq.properties.OperationLogMqProperties;
import com.scott.payment.component.redis.idempotent.IdempotentAcquireResult;
import com.scott.payment.component.redis.idempotent.IdempotentService;
import com.scott.payment.data.service.OperationLogPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogConsumerService
 * @date : 2026-08-01 14:40
 * @email : scott_x@163.com
 * @description : Admin 与 Merchant 操作日志共享消费编排，统一处理反序列化、辅助幂等、失败释放和追踪上下文
 * @status : create
 */
@Slf4j
@Service
public class OperationLogConsumerService {

    /** 操作日志事务写入服务。 */
    private final OperationLogPersistenceService persistenceService;

    /** Redis MQ 辅助幂等服务。 */
    private final IdempotentService idempotentService;

    /** 操作日志 MQ 公共配置。 */
    private final OperationLogMqProperties properties;

    /**
     * 创建操作日志共享消费服务。
     *
     * @param persistenceService 操作日志事务写入服务
     * @param idempotentService  Redis MQ 辅助幂等服务
     * @param properties         操作日志 MQ 配置
     */
    public OperationLogConsumerService(OperationLogPersistenceService persistenceService,
                                       IdempotentService idempotentService,
                                       OperationLogMqProperties properties) {
        this.persistenceService = persistenceService;
        this.idempotentService = idempotentService;
        this.properties = properties;
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
        OperationLogMessage message = parseMessage(source, payload);
        if (message == null) {
            return;
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            String idempotentKey = resolveIdempotentKey(message);
            IdempotentAcquireResult acquireResult = idempotentService.acquireMq(
                    source.getIdempotentNamespace(),
                    idempotentKey,
                    properties.getConsumeIdempotentTtlSeconds()
            );
            if (acquireResult == IdempotentAcquireResult.DUPLICATE) {
                log.info("event: DATA_OPERATION_LOG_DUPLICATE source: {} traceId: {} messageId: {} durationMs: {}",
                        source, TraceContext.getTraceId(), message.getMessageId(), elapsedMillis(startNanos));
                return;
            }
            if (acquireResult == IdempotentAcquireResult.FALLBACK) {
                log.warn("event: DATA_OPERATION_LOG_IDEMPOTENT_FALLBACK source: {} traceId: {} messageId: {} action: continueToDatabaseUniqueConstraint",
                        source, TraceContext.getTraceId(), message.getMessageId());
            }
            try {
                persistenceService.persist(message, idempotentKey);
            } catch (RuntimeException exception) {
                releaseForRetry(source, idempotentKey, acquireResult, message, exception);
                throw exception;
            }
            log.info("event: DATA_OPERATION_LOG_CONSUMED source: {} traceId: {} messageId: {} merchantId: {} module: {} operation: {} status: {} durationMs: {}",
                    source,
                    TraceContext.getTraceId(),
                    message.getMessageId(),
                    message.getMerchantId(),
                    message.getOperationModule(),
                    message.getOperationName(),
                    message.getOperationStatus(),
                    elapsedMillis(startNanos));
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 解析消息且不输出原始载荷，避免畸形消息把敏感内容带入日志。
     *
     * @param source  操作日志来源系统
     * @param payload RocketMQ JSON 消息体
     * @return 消息对象；空载荷返回 null
     */
    private OperationLogMessage parseMessage(OperationLogSource source, String payload) {
        if (!StringUtils.hasText(payload)) {
            log.warn("event: DATA_OPERATION_LOG_INVALID source: {} reason: emptyPayload", source);
            return null;
        }
        try {
            return JsonUtils.parseObject(payload, OperationLogMessage.class);
        } catch (RuntimeException exception) {
            log.error("event: DATA_OPERATION_LOG_INVALID source: {} payloadLength: {} exceptionType: {}",
                    source, payload.length(), exception.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * 解析消息幂等键；旧生产者未填业务键时使用 messageId 保持可重试兼容。
     *
     * @param message 操作日志消息
     * @return 非空幂等键
     */
    private String resolveIdempotentKey(OperationLogMessage message) {
        if (StringUtils.hasText(message.getIdempotentKey())) {
            return message.getIdempotentKey().trim();
        }
        if (StringUtils.hasText(message.getMessageId())) {
            return message.getMessageId().trim();
        }
        throw new IllegalArgumentException("operation log message must provide idempotentKey or messageId");
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
            log.error("event: DATA_OPERATION_LOG_IDEMPOTENT_RELEASE_FAILED source: {} traceId: {} messageId: {}",
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
