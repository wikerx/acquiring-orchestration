package com.sinopay.payment.component.mq.producer;

import com.sinopay.payment.component.mq.message.BaseMqMessage;

public interface MqProducer {

    void send(String topic, String tag, BaseMqMessage message);
}

