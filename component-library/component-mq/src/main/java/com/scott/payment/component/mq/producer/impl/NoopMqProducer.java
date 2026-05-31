package com.scott.payment.component.mq.producer.impl;

import com.scott.payment.component.mq.message.BaseMqMessage;
import com.scott.payment.component.mq.producer.MqProducer;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : NoopMqProducer
 * @date : 2026-05-31 20:53
 * @email : scott_x@163.com
 * @description : RocketMQ 未启用时的安全降级消息发送服务
 * @status : create
 */
@Slf4j
@Service
@ConditionalOnMissingBean(RocketMQTemplate.class)
public class NoopMqProducer implements MqProducer {

    /**
     * 在 RocketMQ 未配置或测试环境中安全跳过消息发送。
     *
     * @param topic   RocketMQ Topic
     * @param tag     RocketMQ Tag
     * @param message 基础消息体
     */
    @Override
    public void send(String topic, String tag, BaseMqMessage message) {
        log.warn("RocketMQ未启用，消息发送已跳过，topic：{}，tag：{}，messageId：{}",
                topic,
                tag,
                message == null ? null : message.getMessageId());
    }
}
