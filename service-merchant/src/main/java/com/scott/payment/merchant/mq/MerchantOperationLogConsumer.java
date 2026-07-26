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
 * @description : MerchantOperationLogConsumer 消息消费组件，用于解析 MQ 消息、绑定链路上下文并触发后续处理，位于 商户后台服务层，输入输出边界由所在包和公开方法契约限定。
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
        OperationLogMessage message = JsonUtils.parseObject(payload, OperationLogMessage.class);
        if (message == null) {
            log.warn("event=MERCHANT_OPERATION_LOG_CONSUME_SKIP reason=messageInvalid payloadLength={}",
                    payload == null ? 0 : payload.length());
            return;
        }
        TraceContext.setTraceId(TraceContext.resolveOrCreate(message.getTraceId()));
        try {
            String idempotentKey = "operation-log:consume:merchant:" + message.getIdempotentKey();
            if (!idempotentService.acquire(idempotentKey, properties.getConsumeIdempotentTtlSeconds())) {
                log.info("event=MERCHANT_OPERATION_LOG_DUPLICATE messageId={} retryCount={} idempotentKey={}",
                        message.getMessageId(),
                        message.getRetryCount(),
                        message.getIdempotentKey());
                return;
            }
            SysOperLogRecordRequest request = operLogMessageConverter.toRecordRequest(message);
            merchantOperLogService.recordOperLog(request);
            log.info("event=MERCHANT_OPERATION_LOG_CONSUMED messageId={} retryCount={} operationModule={} operationType={}",
                    message.getMessageId(),
                    message.getRetryCount(),
                    message.getOperationModule(),
                    message.getOperationType());
        } finally {
            TraceContext.clear();
        }
    }
}
