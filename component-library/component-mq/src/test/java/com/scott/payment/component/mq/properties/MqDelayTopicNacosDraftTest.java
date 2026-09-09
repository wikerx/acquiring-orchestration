package com.scott.payment.component.mq.properties;

import com.scott.payment.component.mq.admin.MqResourceType;
import com.scott.payment.component.mq.constant.MqTopic;
import org.apache.rocketmq.common.attribute.TopicMessageType;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MqDelayTopicNacosDraftTest
 * @date : 2026-08-25
 * @email : scott_x@163.com
 * @description : 加载本地 Nacos RocketMQ 草案，验证交易 FIFO、清分 Delay Topic 及对应消费组声明。
 * @status : create
 */
class MqDelayTopicNacosDraftTest {

    /** 清分补偿 Topic 必须为 RocketMQ 5.x DELAY 类型并声明两个清分消费组。 */
    @Test
    void shouldDeclareClearingDelayTopicAndConsumerGroups() throws Exception {
        MqResourceInitializerProperties properties = bindDraft();

        assertThat(properties.getResources())
                .filteredOn(resource -> MqTopic.PAYMENT_CLEARING_DELAY.equals(resource.getName()))
                .singleElement()
                .satisfies(resource -> {
                    assertThat(resource.getType()).isEqualTo(MqResourceType.TOPIC);
                    assertThat(resource.getMessageType()).isEqualTo(TopicMessageType.DELAY);
                    assertThat(resource.getReadQueueNums()).isEqualTo(4);
                    assertThat(resource.getWriteQueueNums()).isEqualTo(4);
                });
        assertThat(properties.getResources())
                .filteredOn(resource -> resource.getType() == MqResourceType.CONSUMER_GROUP)
                .extracting(MqResourceDefinitionProperties::getName)
                .contains("service-clearing-transaction-terminal",
                        "service-clearing-transaction-retry-due");
    }

    /** 交易生命周期必须使用专用 RocketMQ 5.x FIFO Topic，不与普通通知或 Delay 消息混用。 */
    @Test
    void shouldDeclarePaymentTransactionFifoTopic() throws Exception {
        MqResourceInitializerProperties properties = bindDraft();

        assertThat(properties.getResources())
                .filteredOn(resource -> MqTopic.PAYMENT_TRANSACTION_FIFO.equals(resource.getName()))
                .singleElement()
                .satisfies(resource -> {
                    assertThat(resource.getType()).isEqualTo(MqResourceType.TOPIC);
                    assertThat(resource.getMessageType()).isEqualTo(TopicMessageType.FIFO);
                    assertThat(resource.getReadQueueNums()).isEqualTo(4);
                    assertThat(resource.getWriteQueueNums()).isEqualTo(4);
                });
    }

    private MqResourceInitializerProperties bindDraft() throws Exception {
        Path draftPath = findDraftPath();
        List<PropertySource<?>> yamlSources = new YamlPropertySourceLoader()
                .load(draftPath.getFileName().toString(), new FileSystemResource(draftPath));
        MutablePropertySources propertySources = new MutablePropertySources();
        yamlSources.forEach(propertySources::addLast);
        return new Binder(ConfigurationPropertySources.from(propertySources))
                .bind("acquiring.mq.initializer", Bindable.of(MqResourceInitializerProperties.class))
                .orElseThrow(() -> new IllegalStateException("rocketmq dev draft is missing"));
    }

    /** 从模块或 Reactor 工作目录向上定位仓库级 Nacos 草案。 */
    private Path findDraftPath() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve("docs/deployment/nacos/rocketmq-dev.yaml");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("rocketmq-dev.yaml is missing");
    }
}
