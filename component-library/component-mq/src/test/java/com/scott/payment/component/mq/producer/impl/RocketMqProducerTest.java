package com.scott.payment.component.mq.producer.impl;

import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.message.BaseMqMessage;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RocketMqProducerTest
 * @date : 未确认
 * @email : scott_x@163.com
 * @description : RocketMqProducerTest 自动化测试类，用于验证对应模块的业务规则、异常边界和回归场景，位于 公共组件层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
class RocketMqProducerTest {

    @AfterEach
    void tearDown() {
        TraceContext.clear();
    }

    @Test
    void shouldFillTraceMetadataEvenWhenRocketMqTemplateIsUnavailable() {
        TraceContext.setTraceId("trace-mq-001");
        ObjectProvider<RocketMQTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        RocketMqProducer producer = new RocketMqProducer(provider);
        BaseMqMessage message = new BaseMqMessage();

        producer.send("payment-event", "created", message);

        assertThat(message.getMessageId()).isNotBlank();
        assertThat(message.getCreatedAt()).isNotNull();
        assertThat(message.getTraceId()).isEqualTo("trace-mq-001");
    }
}
