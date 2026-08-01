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
 * @classname : AdminOperationLogConsumer
 * @date : 2026-08-01 14:40
 * @email : scott_x@163.com
 * @description : service-data 的 Admin 操作日志 Topic 适配器，只负责把消息交给共享消费编排
 * @status : create
 */
@Component
@ConditionalOnProperty(prefix = "acquiring.operation-log.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = MqTopic.ADMIN_OPERATION_LOG,
        consumerGroup = DataMqConsumerGroups.ADMIN_OPERATION_LOG,
        messageModel = MessageModel.CLUSTERING
)
public class AdminOperationLogConsumer implements RocketMQListener<String> {

    /** 操作日志共享消费编排。 */
    private final OperationLogConsumerService consumerService;

    /**
     * 创建 Admin 操作日志 Topic 适配器。
     *
     * @param consumerService 操作日志共享消费编排
     */
    public AdminOperationLogConsumer(OperationLogConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    /**
     * 接收 Admin 操作日志消息。
     *
     * @param payload RocketMQ JSON 消息体
     */
    @Override
    public void onMessage(String payload) {
        consumerService.consume(OperationLogSource.ADMIN, payload);
    }
}
