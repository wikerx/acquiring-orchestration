package com.global.payment.component.mq.producer;

import com.global.payment.component.mq.message.BaseMqMessage;

public interface MqProducer {

    void send(String topic, String tag, BaseMqMessage message);
}

