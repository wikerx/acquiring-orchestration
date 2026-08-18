package com.scott.payment.data.mq;

import com.scott.payment.component.mq.constant.MqTopic;
import org.apache.rocketmq.spring.annotation.MessageModel;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOperationLogConsumer
 * @date : 2026-08-01 14:40
 * @email : scott_x@163.com
 * @description : service-data 的 Merchant 操作日志 Topic 适配器，只负责把消息交给共享消费编排
 * @status : create
 */
@Component
@ConditionalOnProperty(prefix = "acquiring.operation-log.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = MqTopic.MERCHANT_OPERATION_LOG,
        consumerGroup = DataMqConsumerGroups.MERCHANT_OPERATION_LOG,
        messageModel = MessageModel.CLUSTERING
)
public class MerchantOperationLogConsumer implements RocketMQListener<String> {

    /** 操作日志共享消费编排。 */
    private final OperationLogConsumerService consumerService;

    /**
     * 创建 Merchant 操作日志 Topic 适配器。
     *
     * @param consumerService 操作日志共享消费编排
     */
    public MerchantOperationLogConsumer(OperationLogConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    /**
     * 接收 Merchant 操作日志消息。
     *
     * @param payload RocketMQ JSON 消息体
     */
    @Override
    public void onMessage(String payload) {
        consumerService.consume(OperationLogSource.MERCHANT, payload);
    }
}
