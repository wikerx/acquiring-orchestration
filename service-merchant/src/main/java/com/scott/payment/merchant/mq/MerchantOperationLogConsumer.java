package com.scott.payment.merchant.mq;

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
public class MerchantOperationLogConsumer implements RocketMQListener<OperationLogMessage> {

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
     * 创建商户操作日志消费者。
     *
     * @param merchantOperLogService 商户操作日志领域服务
     * @param idempotentService Redis 幂等服务
     * @param properties 操作日志 MQ 配置
     */
    public MerchantOperationLogConsumer(MerchantOperLogService merchantOperLogService,
                                        IdempotentService idempotentService,
                                        OperationLogMqProperties properties) {
        this.merchantOperLogService = merchantOperLogService;
        this.idempotentService = idempotentService;
        this.properties = properties;
    }

    /**
     * 消费商户操作日志消息。
     *
     * @param message 操作日志消息
     */
    @Override
    public void onMessage(OperationLogMessage message) {
        if (message == null) {
            return;
        }
        String idempotentKey = "operation-log:consume:merchant:" + message.getIdempotentKey();
        if (!idempotentService.acquire(idempotentKey, properties.getConsumeIdempotentTtlSeconds())) {
            log.info("商户操作日志重复消息已跳过，messageId：{}，idempotentKey：{}",
                    message.getMessageId(),
                    message.getIdempotentKey());
            return;
        }
        SysOperLogRecordRequest request = OperLogMessageConverter.INSTANCE.toRecordRequest(message);
        merchantOperLogService.recordOperLog(request);
    }
}
