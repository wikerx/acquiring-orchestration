package com.scott.payment.channel.payment.worldpay.config;

import com.scott.payment.channel.payment.worldpay.callback.CallbackProtocol;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackProtocol;
import com.scott.payment.channel.payment.api.PaymentChannelClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Worldpay 插件自动配置测试，验证 JSON/XML Provider 成组启用和关闭。
 */
class ChannelAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChannelAutoConfiguration.class));

    @Test
    void shouldEnableWorldPayProviderByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(com.scott.payment.channel.payment.worldpay.json.adapter.PaymentChannelAdapter.class);
            assertThat(context).hasSingleBean(com.scott.payment.channel.payment.worldpay.xml.adapter.PaymentChannelAdapter.class);
            assertThat(context.getBeansOfType(PaymentChannelClient.class)).hasSize(2);
            assertThat(context).hasSingleBean(CallbackProtocol.class);
            assertThat(context.getBeansOfType(PaymentChannelCallbackProtocol.class)).hasSize(1);
        });
    }

    @Test
    void shouldDisableAllWorldPayProviderBeans() {
        contextRunner.withPropertyValues("payment.channel.worldpay.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(com.scott.payment.channel.payment.worldpay.json.adapter.PaymentChannelAdapter.class));
    }
}
