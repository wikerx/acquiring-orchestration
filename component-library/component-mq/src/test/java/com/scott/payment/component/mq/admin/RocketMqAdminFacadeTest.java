package com.scott.payment.component.mq.admin;

import com.scott.payment.component.mq.properties.MqResourceDefinitionProperties;
import com.scott.payment.component.mq.properties.MqResourceInitializerProperties;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.attribute.TopicMessageType;
import org.apache.rocketmq.spring.autoconfigure.RocketMQProperties;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RocketMqAdminFacadeTest
 * @date : 2026-08-25
 * @email : scott_x@163.com
 * @description : 验证 RocketMQ 5.x Topic 消息类型声明和已有资源一致性门禁，不连接真实 Broker。
 * @status : create
 */
class RocketMqAdminFacadeTest {

    /** Delay Topic 声明必须写入官方 TopicConfig，不能只依赖发送端的投递时间。 */
    @Test
    void shouldBuildDelayTopicConfig() {
        RocketMqAdminFacade facade = newFacade();
        MqResourceDefinitionProperties resource = topic("acquiring_payment_clearing_delay_topic");
        resource.setMessageType(TopicMessageType.DELAY);

        TopicConfig topicConfig = facade.buildTopicConfig(resource);

        assertThat(topicConfig.getTopicMessageType()).isEqualTo(TopicMessageType.DELAY);
        assertThat(topicConfig.getReadQueueNums()).isEqualTo(4);
        assertThat(topicConfig.getWriteQueueNums()).isEqualTo(4);
    }

    /** 已存在的 NORMAL Topic 不能被同名 DELAY 声明静默复用或覆盖。 */
    @Test
    void shouldRejectExistingNormalTopicWhenDelayIsDeclared() {
        RocketMqAdminFacade facade = newFacade();
        MqResourceDefinitionProperties resource = topic("acquiring_payment_clearing_delay_topic");
        resource.setMessageType(TopicMessageType.DELAY);
        TopicConfig expected = new TopicConfig(resource.getName());
        expected.setTopicMessageType(TopicMessageType.DELAY);
        TopicConfig existing = new TopicConfig(resource.getName());
        existing.setTopicMessageType(TopicMessageType.NORMAL);

        assertThatIllegalStateException()
                .isThrownBy(() -> facade.validateExistingTopicMessageTypes(
                        resource, Map.of("broker-a:10911", expected, "broker-b:10911", existing)))
                .withMessageContaining("topic=acquiring_payment_clearing_delay_topic")
                .withMessageContaining("brokerAddress=broker-b:10911")
                .withMessageContaining("expected=DELAY")
                .withMessageContaining("actual=NORMAL");
    }

    /** 未配置 message-type 的既有资源继续按 NORMAL 兼容，避免影响原 Topic。 */
    @Test
    void shouldDefaultToNormalAndAcceptExistingNormalTopic() {
        RocketMqAdminFacade facade = newFacade();
        MqResourceDefinitionProperties resource = topic("payment-event");
        TopicConfig existing = new TopicConfig(resource.getName());
        existing.setTopicMessageType(TopicMessageType.NORMAL);

        facade.validateExistingTopicMessageTypes(resource, Map.of("broker-a:10911", existing));

        assertThat(facade.buildTopicConfig(resource).getTopicMessageType())
                .isEqualTo(TopicMessageType.NORMAL);
    }

    private RocketMqAdminFacade newFacade() {
        return new RocketMqAdminFacade(new RocketMQProperties(), new MqResourceInitializerProperties());
    }

    private MqResourceDefinitionProperties topic(String name) {
        MqResourceDefinitionProperties resource = new MqResourceDefinitionProperties();
        resource.setName(name);
        resource.setType(MqResourceType.TOPIC);
        return resource;
    }
}
