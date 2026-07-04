package com.scott.payment.component.mq.producer.impl;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.producer.MqProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RocketMqProducer
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Rocket Mq Producer，位于 component-library/component-mq 的消息消费层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Slf4j
@Service
public class RocketMqProducer implements MqProducer {

    /**
     * RocketMQ Spring 模板。
     */
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;

    /**
     * 创建 RocketMQ 消息发送服务。
     *
     * @param rocketMQTemplateProvider RocketMQ Spring 模板提供器
     */
    public RocketMqProducer(ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider) {
        this.rocketMQTemplateProvider = rocketMQTemplateProvider;
    }

    /**
     * 发送普通同步消息。
     *
     * @param topic   RocketMQ Topic
     * @param tag     RocketMQ Tag，用于消费者过滤业务类型
     * @param message 基础消息体
     */
    /**
     * 发送收单支付消息或外部请求，并记录必要的执行结果。
     * @param topic 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param tag 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param message 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    public void send(String topic, String tag, BaseMqMessage message) {
        Objects.requireNonNull(message, "mq message can not be null");
        if (!StringUtils.hasText(topic)) {
            throw new IllegalArgumentException("rocketmq topic can not be blank");
        }
        RocketMQTemplate rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
        if (rocketMQTemplate == null) {
            log.warn("RocketMQTemplate未就绪，消息发送已跳过，topic：{}，tag：{}，messageId：{}",
                    topic,
                    tag,
                    message.getMessageId());
            return;
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
