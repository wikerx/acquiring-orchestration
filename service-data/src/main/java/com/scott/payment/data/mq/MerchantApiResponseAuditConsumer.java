package com.scott.payment.data.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.MerchantApiResponseAuditMessage;
import com.scott.payment.data.service.MerchantApiResponseAuditPersistenceService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

/**
 * OpenAPI 商户响应加密审计消费者。
 *
 * <p>消费者只接受 service-openapi 生产的固定事件，使用交易真实时间精确路由分片表。
 * RocketMQ 至少一次投递由数据库条件更新和事实回查兜底，日志禁止输出脱敏正文或任何密文。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "data.merchant-api-response-audit.mq", name = "enabled",
        havingValue = "true", matchIfMissing = false)
@RocketMQMessageListener(
        topic = MqTopic.PAYMENT_EVENT,
        consumerGroup = DataMqConsumerGroups.MERCHANT_API_RESPONSE_AUDIT,
        selectorExpression = MqTag.MERCHANT_API_RESPONSE_AUDIT,
        messageModel = MessageModel.CLUSTERING
)
public class MerchantApiResponseAuditConsumer implements RocketMQListener<String> {

    private static final String PRODUCER_SERVICE = "service-openapi";

    private static final String SHA256_HEX_PATTERN = "[0-9a-f]{64}";

    private final MerchantApiResponseAuditPersistenceService persistenceService;

    /**
     * 创建商户响应审计消费者。
     *
     * @param persistenceService 审计事务落库服务
     */
    public MerchantApiResponseAuditConsumer(
            MerchantApiResponseAuditPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    /**
     * 解析、校验并幂等持久化一条商户响应审计消息。
     *
     * @param payload RocketMQ JSON 消息体；日志中禁止输出原文
     */
    @Override
    public void onMessage(String payload) {
        long startNanos = System.nanoTime();
        MerchantApiResponseAuditMessage message = parse(payload);
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            validate(message);
            MerchantApiResponseAuditPersistenceService.PersistenceResult result =
                    persistenceService.persist(message);
            log.info("商户OpenAPI响应审计消费完成, event: DATA_MERCHANT_API_RESPONSE_AUDIT_CONSUMED, traceId: {}, messageId: {}, transactionId: {}, requestId: {}, result: {}, durationMs: {}",
                    TraceContext.getTraceId(), message.getMessageId(), message.getTransactionId(),
                    message.getRequestId(), result, elapsedMillis(startNanos));
        } finally {
            TraceContext.clear();
        }
    }

    /**
     * 解析消息载荷，畸形消息只记录长度和异常类型。
     *
     * @param payload RocketMQ JSON 消息体
     * @return 已反序列化消息
     */
    private MerchantApiResponseAuditMessage parse(String payload) {
        if (!StringUtils.hasText(payload)) {
            throw new IllegalArgumentException("merchant response audit payload is empty");
        }
        try {
            MerchantApiResponseAuditMessage message = JsonUtils.parseObject(
                    payload, MerchantApiResponseAuditMessage.class);
            if (message == null) {
                throw new IllegalArgumentException("merchant response audit payload is invalid");
            }
            return message;
        } catch (RuntimeException exception) {
            log.error("商户响应审计消息解析失败, event: DATA_MERCHANT_API_RESPONSE_AUDIT_DESERIALIZE_FAILED, payloadLength: {}, exceptionType: {}",
                    payload.length(), exception.getClass().getSimpleName());
            throw new IllegalArgumentException("merchant response audit payload is invalid", exception);
        }
    }

    /**
     * 校验消息来源、稳定身份、分片时间、摘要格式和数据库字段容量。
     *
     * @param message 已反序列化消息
     */
    private void validate(MerchantApiResponseAuditMessage message) {
        requireText(message.getMessageId(), 64, "messageId");
        requireText(message.getEventType(), 64, "eventType");
        requireText(message.getProducerService(), 64, "producerService");
        if (!MqTag.MERCHANT_API_RESPONSE_AUDIT.equals(message.getEventType())
                || !PRODUCER_SERVICE.equals(message.getProducerService())) {
            throw invalid("event type or producer service is inconsistent");
        }
        requireText(message.getTransactionId(), 64, "transactionId");
        requireOptionalText(message.getRequestId(), 128, "requestId");
        requireDatabaseTime(message.getTransactionDateTime(), "transactionDateTime");
        requireDatabaseTime(message.getResponseTime(), "responseTime");
        if (message.getCreatedAt() == null) {
            throw invalid("createdAt is required");
        }
        requireText(message.getResponseCipherDigest(), 64, "responseCipherDigest");
        if (!message.getResponseCipherDigest().matches(SHA256_HEX_PATTERN)) {
            throw invalid("responseCipherDigest must be lowercase SHA-256 hex");
        }
        requireOptionalText(message.getResponseCipherMasked(), 128, "responseCipherMasked");
        if (!StringUtils.hasText(message.getResponseCipherMasked())) {
            throw invalid("responseCipherMasked is required");
        }
        if (message.getResponsePlainJsonMasked() != null
                && message.getResponsePlainJsonMasked().length() > 16_000) {
            throw invalid("responsePlainJsonMasked exceeds 16000 characters");
        }
        String expectedMessageId = MerchantApiResponseAuditMessage.stableMessageId(
                message.getTransactionId(), message.getTransactionDateTime(), message.getRequestId());
        if (!expectedMessageId.equals(message.getMessageId())) {
            throw invalid("messageId does not match the stable audit identity");
        }
    }

    /**
     * 校验必填文本没有首尾空白且未超过数据库容量。
     *
     * @param value 字段值
     * @param maxLength 最大字符数
     * @param fieldName 字段名称
     */
    private void requireText(String value, int maxLength, String fieldName) {
        if (!StringUtils.hasText(value) || !value.equals(value.trim()) || value.length() > maxLength) {
            throw invalid(fieldName + " is invalid");
        }
    }

    /**
     * 校验可选文本为空或满足数据库字段容量。
     *
     * @param value 字段值
     * @param maxLength 最大字符数
     * @param fieldName 字段名称
     */
    private void requireOptionalText(String value, int maxLength, String fieldName) {
        if (value != null && (!StringUtils.hasText(value)
                || !value.equals(value.trim()) || value.length() > maxLength)) {
            throw invalid(fieldName + " is invalid");
        }
    }

    /**
     * 校验具体业务时间存在且符合交易表 DATETIME(3) 精度。
     *
     * @param value 业务时间
     * @param fieldName 字段名称
     */
    private void requireDatabaseTime(LocalDateTime value, String fieldName) {
        if (value == null || value.getNano() % 1_000_000 != 0) {
            throw invalid(fieldName + " must use millisecond precision");
        }
    }

    /**
     * 创建不携带消息正文的稳定参数异常。
     *
     * @param reason 校验失败原因
     * @return 参数异常
     */
    private IllegalArgumentException invalid(String reason) {
        return new IllegalArgumentException("merchant response audit message is invalid: " + reason);
    }

    /**
     * 计算消费耗时，使用单调时钟避免系统时间校准影响。
     *
     * @param startNanos 消费开始的单调时钟值
     * @return 已耗时毫秒数
     */
    private long elapsedMillis(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }
}
