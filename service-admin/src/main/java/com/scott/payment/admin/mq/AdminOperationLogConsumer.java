package com.scott.payment.admin.mq;

import com.scott.payment.admin.converter.OperLogMessageConverter;
import com.scott.payment.admin.dto.SysOperLogRecordRequest;
import com.scott.payment.admin.service.AdminOperLogService;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.OperationLogMessage;
import com.scott.payment.component.mq.properties.OperationLogMqProperties;
import com.scott.payment.component.redis.idempotent.IdempotentAcquireResult;
import com.scott.payment.component.redis.idempotent.IdempotentService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOperationLogConsumer
 * @date : 2026-06-20 01:54
 * @email : scott_x@163.com
 * @description : 消费管理端操作日志消息；Redis 负责快速去重，sys_oper_log 唯一键承担最终幂等
 * @status : create
 *
 * <p>Redis 降级时仍继续落库，禁止因缓存故障丢失审计日志；只有真实取得 Redis 占用且落库失败时才释放。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "acquiring.operation-log.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = MqTopic.ADMIN_OPERATION_LOG,
        consumerGroup = AdminOperationLogMqConstants.ADMIN_OPERATION_LOG_CONSUMER_GROUP,
        messageModel = MessageModel.CLUSTERING
)
public class AdminOperationLogConsumer implements RocketMQListener<String> {

    /** 管理端操作日志 MQ 消费幂等命名空间，与其他消费者业务键隔离。 */
    private static final String IDEMPOTENT_NAMESPACE = "admin-operation-log";

    /**
     * 操作日志领域服务。
     */
    private final AdminOperLogService adminOperLogService;

    /**
     * Redis 幂等服务。
     */
    private final IdempotentService idempotentService;

    /**
     * 操作日志 MQ 配置。
     */
    private final OperationLogMqProperties properties;
    /**
     * 操作日志 MQ 消息转换器。
     */
    private final OperLogMessageConverter operLogMessageConverter;

    /**
     * 创建后台操作日志消费者。
     *
     * @param adminOperLogService 操作日志领域服务
     * @param idempotentService   Redis 幂等服务
     * @param properties          操作日志 MQ 配置
     * @param operLogMessageConverter 操作日志 MQ 消息转换器
     */
    public AdminOperationLogConsumer(AdminOperLogService adminOperLogService,
                                     IdempotentService idempotentService,
                                     OperationLogMqProperties properties,
                                     OperLogMessageConverter operLogMessageConverter) {
        this.adminOperLogService = adminOperLogService;
        this.idempotentService = idempotentService;
        this.properties = properties;
        this.operLogMessageConverter = operLogMessageConverter;
    }

    /**
     * 消费后台操作日志消息。
     *
     * @param payload 操作日志消息 JSON 字符串
     */
    @Override
    public void onMessage(String payload) {
        long startNanos = System.nanoTime();
        OperationLogMessage message = JsonUtils.parseObject(payload, OperationLogMessage.class);
        if (message == null) {
            log.warn("event: ADMIN_OPERATION_LOG_CONSUME_SKIP stage=MQ_CONSUME traceId: {} reason=messageInvalid payloadLength: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    payload == null ? 0 : payload.length(),
                    elapsedMillis(startNanos));
            return;
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            log.info("event: ADMIN_OPERATION_LOG_CONSUME_START stage=MQ_CONSUME traceId: {} messageId: {} retryCount: {} operationModule: {} operationType: {} operatorId: {} merchantId: {} requestUri: {}",
                    TraceContext.getTraceId(),
                    message.getMessageId(),
                    message.getRetryCount(),
                    message.getOperationModule(),
                    message.getOperationType(),
                    message.getOperatorId(),
                    message.getMerchantId(),
                    message.getRequestUri());
            String idempotentKey = message.getIdempotentKey();
            IdempotentAcquireResult acquireResult = idempotentService.acquireMq(
                    IDEMPOTENT_NAMESPACE,
                    idempotentKey,
                    properties.getConsumeIdempotentTtlSeconds()
            );
            if (acquireResult == IdempotentAcquireResult.DUPLICATE) {
                log.info("event: ADMIN_OPERATION_LOG_DUPLICATE stage=MQ_CONSUME traceId: {} messageId: {} retryCount: {} operationModule: {} operationType: {} durationMs: {}",
                        TraceContext.getTraceId(),
                        message.getMessageId(),
                        message.getRetryCount(),
                        message.getOperationModule(),
                        message.getOperationType(),
                        elapsedMillis(startNanos));
                return;
            }
            if (acquireResult == IdempotentAcquireResult.FALLBACK) {
                log.warn("event: ADMIN_OPERATION_LOG_IDEMPOTENT_FALLBACK stage=MQ_CONSUME traceId: {} "
                                + "messageId: {} action: continueToDatabaseUniqueConstraint",
                        TraceContext.getTraceId(),
                        message.getMessageId());
            }
            try {
                SysOperLogRecordRequest request = operLogMessageConverter.toRecordRequest(message);
                adminOperLogService.recordOperLog(request);
            } catch (RuntimeException exception) {
                releaseForRetry(idempotentKey, acquireResult, message, exception);
                throw exception;
            }
            log.info("event: ADMIN_OPERATION_LOG_CONSUMED stage=MQ_CONSUME traceId: {} messageId: {} retryCount: {} operationModule: {} operationName: {} operationType: {} operatorId: {} merchantId: {} requestUri: {} operationStatus: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    message.getMessageId(),
                    message.getRetryCount(),
                    message.getOperationModule(),
                    message.getOperationName(),
                    message.getOperationType(),
                    message.getOperatorId(),
                    message.getMerchantId(),
                    message.getRequestUri(),
                    message.getOperationStatus(),
                    elapsedMillis(startNanos));
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 计算单条操作日志消息消费耗时。
     *
     * @param startNanos System.nanoTime 起始值
     * @return 耗时毫秒数
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 业务写入失败后释放本次实际取得的 Redis 占用。
     *
     * <p>FALLBACK 没有 Redis 所有权，禁止执行释放；否则可能删除其他实例刚写入的摘要。</p>
     *
     * @param idempotentKey    消息业务幂等键
     * @param acquireResult    本次 Redis 获取结果
     * @param message          操作日志消息，仅用于非敏感追踪字段
     * @param originalException 原始业务异常，释放异常会作为 suppressed 附加
     */
    private void releaseForRetry(String idempotentKey,
                                 IdempotentAcquireResult acquireResult,
                                 OperationLogMessage message,
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
            log.error("event: ADMIN_OPERATION_LOG_IDEMPOTENT_RELEASE_FAILED stage=MQ_CONSUME traceId: {} messageId: {}",
                    TraceContext.getTraceId(),
                    message.getMessageId(),
                    releaseException);
        }
    }
}
