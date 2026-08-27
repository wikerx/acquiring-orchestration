package com.scott.payment.component.mq.publisher;

import com.scott.payment.component.mq.enums.OperationLogSystemCode;
import com.scott.payment.component.mq.message.OperationLogMessage;
import com.scott.payment.component.mq.properties.OperationLogMqProperties;
import com.scott.payment.component.web.operation.dto.OperationLogRecord;
import com.scott.payment.component.web.operation.service.OperationLogPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OperationLogMqPublisher
 * @date : 2026-06-20 01:36
 * @email : scott_x@163.com
 * @description : 基于 RocketMQ 的管理类系统操作日志发布器
 * @status : create
 *
 * <p>该发布器只负责把组件层采集好的日志记录转换为 MQ 消息并发送，
 * 不承担业务落库和消费幂等判断职责。</p>
 */
@Slf4j
@Component
@ConditionalOnBean(OperationLogSystemCode.class)
@ConditionalOnProperty(prefix = "acquiring.operation-log.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OperationLogMqPublisher implements OperationLogPublisher {

    /**
     * MQ 消息发送器。
     */
    private final IndependentReliableMqPublisher mqPublisher;

    /**
     * 操作日志 MQ 配置。
     */
    private final OperationLogMqProperties properties;

    /**
     * 当前服务所属系统编码。
     */
    private final OperationLogSystemCode systemCode;

    /**
     * 操作日志 Topic 解析器。
     */
    private final OperationLogTopicResolver topicResolver;

    /**
     * 操作日志消息截断器。
     */
    private final OperationLogMessageSanitizer messageSanitizer;

    /**
     * 创建 RocketMQ 操作日志发布器。
     *
     * @param mqPublisher 可靠消息发布器
     * @param properties 操作日志 MQ 配置
     * @param systemCode 当前系统编码
     */
    public OperationLogMqPublisher(IndependentReliableMqPublisher mqPublisher,
                                   OperationLogMqProperties properties,
                                   OperationLogSystemCode systemCode,
                                   OperationLogTopicResolver topicResolver,
                                   OperationLogMessageSanitizer messageSanitizer) {
        this.mqPublisher = mqPublisher;
        this.properties = properties;
        this.systemCode = systemCode;
        this.topicResolver = topicResolver;
        this.messageSanitizer = messageSanitizer;
    }

    /**
     * 发布操作日志消息。
     *
     * @param record 已完成脱敏和截断的操作日志记录
     */
    @Override
    public void publish(OperationLogRecord record) {
        if (record == null) {
            return;
        }
        OperationLogMessage message = buildMessage(record);
        mqPublisher.publish(resolveTopic(), null, message);
        log.info("操作日志消息已进入可靠投递队列，systemCode：{}，topic：{}，messageId：{}，requestId：{}",
                systemCode.name(),
                resolveTopic(),
                message.getMessageId(),
                message.getRequestId());
    }

    /**
     * 构造操作日志消息体。
     *
     * @param record 操作日志记录
     * @return MQ 消息
     */
    private OperationLogMessage buildMessage(OperationLogRecord record) {
        LocalDateTime operationTime = LocalDateTime.now();
        OperationLogMessage message = new OperationLogMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setSystemCode(systemCode.name());
        message.setRequestId(record.getRequestId());
        message.setTraceId(record.getTraceId());
        message.setOperationModule(record.getModuleName());
        message.setOperationName(record.getOperationName());
        message.setOperationType(String.valueOf(record.getBusinessType()));
        message.setMethodName(record.getMethodName());
        message.setRequestMethod(record.getRequestMethod());
        message.setRequestUri(record.getOperUrl());
        message.setOperatorId(record.getOperatorId());
        message.setOperatorName(record.getOperatorName());
        message.setOperatorType(record.getOperatorType() == null ? null : String.valueOf(record.getOperatorType()));
        message.setMerchantId(record.getMerchantId());
        message.setStoreId(record.getStoreId());
        message.setClientIp(record.getOperIp());
        message.setUserAgent(record.getUserAgent());
        message.setRequestParams(truncate(record.getRequestParam()));
        message.setResponseResult(truncate(record.getResponseResult()));
        message.setErrorMessage(messageSanitizer.sanitize(
                record.getErrorMsg(), OperationLogMessage.ERROR_MESSAGE_MAX_LENGTH));
        message.setOperationStatus(record.getStatus());
        message.setCostTimeMs(record.getCostTime());
        message.setOperationTime(operationTime);
        message.setErrorCode(messageSanitizer.sanitize(
                record.getErrorCode(), OperationLogMessage.ERROR_CODE_MAX_LENGTH));
        message.setIdempotentKey(buildIdempotentKey(record, operationTime));
        return message;
    }

    /**
     * 解析当前系统应发送到的 Topic。
     *
     * @return RocketMQ Topic
     */
    private String resolveTopic() {
        return topicResolver.resolve(systemCode);
    }

    /**
     * 构造消费幂等键。
     *
     * @param record 操作日志记录
     * @param operationTime 操作时间
     * @return 幂等键
     */
    private String buildIdempotentKey(OperationLogRecord record, LocalDateTime operationTime) {
        if (!StringUtils.hasText(record.getRequestId())) {
            return systemCode.name() + ":" + messageIdFallback(record) + ":" + operationTime;
        }
        return systemCode.name() + ":" + record.getRequestId() + ":" + record.getMethodName() + ":" + operationTime;
    }

    /**
     * 在 requestId 缺失时生成兜底幂等片段。
     *
     * @param record 操作日志记录
     * @return 兜底幂等片段
     */
    private String messageIdFallback(OperationLogRecord record) {
        if (StringUtils.hasText(record.getTraceId())) {
            return "trace:" + record.getTraceId();
        }
        if (StringUtils.hasText(record.getMethodName())) {
            return "method:" + record.getMethodName();
        }
        return "message:" + UUID.randomUUID();
    }

    /**
     * 对消息文本再次兜底截断，避免超长日志正文导致消息体无限膨胀。
     *
     * @param value 原始文本
     * @return 截断后文本
     */
    private String truncate(String value) {
        return messageSanitizer.sanitize(value);
    }
}
