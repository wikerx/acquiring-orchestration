package com.scott.payment.component.mq.producer.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.producer.MqProducer;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RocketMqProducer
 * @date : 2026-05-31 20:52
 * @email : scott_x@163.com
 * @description : RocketMQ 消息发送服务实现
 * @status : create
 */
@Service
@ConditionalOnBean(RocketMQTemplate.class)
public class RocketMqProducer implements MqProducer {

    /**
     * RocketMQ Spring 模板。
     */
    private final RocketMQTemplate rocketMQTemplate;

    /**
     * 创建 RocketMQ 消息发送服务。
     *
     * @param rocketMQTemplate RocketMQ Spring 模板
     */
    public RocketMqProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    /**
     * 发送普通同步消息。
     *
     * @param topic   RocketMQ Topic
     * @param tag     RocketMQ Tag，用于消费者过滤业务类型
     * @param message 基础消息体
     */
    @Override
    public void send(String topic, String tag, BaseMqMessage message) {
        Objects.requireNonNull(message, "mq message can not be null");
        if (!StringUtils.hasText(topic)) {
            throw new IllegalArgumentException("rocketmq topic can not be blank");
        }
        fillMessageMetadata(message);
        String destination = StringUtils.hasText(tag) ? topic + ":" + tag : topic;
        rocketMQTemplate.syncSend(destination, MessageBuilder.withPayload(JsonUtils.toJsonString(message)).build());
    }

    /**
     * 补齐消息元数据。
     *
     * @param message 基础消息体
     */
    private void fillMessageMetadata(BaseMqMessage message) {
        if (!StringUtils.hasText(message.getMessageId())) {
            message.setMessageId(UUID.randomUUID().toString());
        }
        if (message.getCreatedAt() == null) {
            message.setCreatedAt(LocalDateTime.now());
        }
    }
}
