package com.scott.payment.component.mq.producer;

import com.scott.payment.component.mq.message.BaseMqMessage;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MqProducer
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 消息发送服务接口
 * @status : create
 */
public interface MqProducer {

    /**
     * 发送普通消息。
     *
     * @param topic   RocketMQ Topic
     * @param tag     RocketMQ Tag，用于消费者过滤业务类型
     * @param message 基础消息体
     */
    void send(String topic, String tag, BaseMqMessage message);
}
