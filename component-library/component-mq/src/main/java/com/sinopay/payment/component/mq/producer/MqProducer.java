package com.sinopay.payment.component.mq.producer;

import com.sinopay.payment.component.mq.message.BaseMqMessage;

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

    void send(String topic, String tag, BaseMqMessage message);
}

