package com.scott.payment.merchant.mq;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.OperationLogMessage;
import com.scott.payment.component.mq.properties.OperationLogMqProperties;
import com.scott.payment.component.redis.idempotent.IdempotentService;
import com.scott.payment.merchant.converter.OperLogMessageConverter;
import com.scott.payment.merchant.dto.SysOperLogRecordRequest;
import com.scott.payment.merchant.service.MerchantOperLogService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOperationLogConsumer
 * @date : 2026-06-20 10:32
 * @email : scott_x@163.com
 * @description : service-merchant 操作日志 MQ 消费者
 * @status : create
 *
 * <p>负责消费商户管理系统操作日志消息，并在本地做幂等控制后落库到 sys_oper_log。</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "acquiring.operation-log.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = MqTopic.MERCHANT_OPERATION_LOG,
        consumerGroup = MerchantOperationLogMqConstants.MERCHANT_OPERATION_LOG_CONSUMER_GROUP,
        messageModel = MessageModel.CLUSTERING
)
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOperationLogConsumer
 * @date : 2026-06-20 10:32
 * @email : scott_x@163.com
 * @description : Merchant Operation Log Consumer 消息消费组件，位于 商户后台服务，解析 MQ 消息、绑定 traceId 和重试次数，并触发后续业务处理。
 * @status : create
 */
public class MerchantOperationLogConsumer implements RocketMQListener<String> {

    /**
     * 商户管理系统操作日志领域服务。
     */
    private final MerchantOperLogService merchantOperLogService;

    /**
     * Redis 幂等服务。
     */
    private final IdempotentService idempotentService;

    /**
     * 操作日志 MQ 配置。
     */
    private final OperationLogMqProperties properties;
    /**
     * 商户操作日志 MQ 消息转换器。
     */
    private final OperLogMessageConverter operLogMessageConverter;

    /**
     * 创建商户操作日志消费者。
     *
     * @param merchantOperLogService 商户操作日志领域服务
     * @param idempotentService       Redis 幂等服务
     * @param properties              操作日志 MQ 配置
     * @param operLogMessageConverter 商户操作日志 MQ 消息转换器
     */
    public MerchantOperationLogConsumer(MerchantOperLogService merchantOperLogService,
                                        IdempotentService idempotentService,
                                        OperationLogMqProperties properties,
                                        OperLogMessageConverter operLogMessageConverter) {
        this.merchantOperLogService = merchantOperLogService;
        this.idempotentService = idempotentService;
        this.properties = properties;
        this.operLogMessageConverter = operLogMessageConverter;
    }

    /**
     * 消费商户操作日志消息。
     *
     * @param payload 操作日志消息 JSON 字符串
     */
    @Override
    public void onMessage(String payload) {
        long startNanos = System.nanoTime();
        OperationLogMessage message = JsonUtils.parseObject(payload, OperationLogMessage.class);
        if (message == null) {
            log.warn("event: MERCHANT_OPERATION_LOG_CONSUME_SKIP stage=MQ_CONSUME traceId: {} reason=messageInvalid payloadLength: {} durationMs: {}",
                    TraceContext.getTraceId(),
                    payload == null ? 0 : payload.length(),
                    elapsedMillis(startNanos));
            return;
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            log.info("event: MERCHANT_OPERATION_LOG_CONSUME_START stage=MQ_CONSUME traceId: {} messageId: {} retryCount: {} operationModule: {} operationType: {} operatorId: {} merchantId: {} requestUri: {}",
                    TraceContext.getTraceId(),
                    message.getMessageId(),
                    message.getRetryCount(),
                    message.getOperationModule(),
                    message.getOperationType(),
                    message.getOperatorId(),
                    message.getMerchantId(),
                    message.getRequestUri());
            String idempotentKey = "operation-log:consume:merchant:" + message.getIdempotentKey();
            if (!idempotentService.acquire(idempotentKey, properties.getConsumeIdempotentTtlSeconds())) {
                log.info("event: MERCHANT_OPERATION_LOG_DUPLICATE stage=MQ_CONSUME traceId: {} messageId: {} retryCount: {} operationModule: {} operationType: {} idempotentKey: {} durationMs: {}",
                        TraceContext.getTraceId(),
                        message.getMessageId(),
                        message.getRetryCount(),
                        message.getOperationModule(),
                        message.getOperationType(),
                        message.getIdempotentKey(),
                        elapsedMillis(startNanos));
                return;
            }
            SysOperLogRecordRequest request = operLogMessageConverter.toRecordRequest(message);
            merchantOperLogService.recordOperLog(request);
            log.info("event: MERCHANT_OPERATION_LOG_CONSUMED stage=MQ_CONSUME traceId: {} messageId: {} retryCount: {} operationModule: {} operationName: {} operationType: {} operatorId: {} merchantId: {} requestUri: {} operationStatus: {} durationMs: {}",
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
}
