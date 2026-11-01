package com.scott.payment.channel.payment.mpgs.config;

import com.scott.payment.channel.payment.mpgs.adapter.PaymentChannelAdapter;
import com.scott.payment.channel.payment.mpgs.callback.CallbackHandler;
import com.scott.payment.channel.payment.mpgs.callback.CallbackProtocol;
import com.scott.payment.channel.payment.mpgs.callback.CallbackVerifier;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackProtocol;
import com.scott.payment.channel.payment.api.PaymentChannelClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MPGS 插件自动配置测试，验证兼容默认启用及显式关闭行为。
 */
class ChannelAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChannelAutoConfiguration.class));

    @Test
    void shouldEnableMpgsProviderByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PaymentChannelAdapter.class);
            assertThat(context).hasSingleBean(CallbackHandler.class);
            assertThat(context).hasSingleBean(CallbackVerifier.class);
            assertThat(context).hasSingleBean(CallbackProtocol.class);
            assertThat(context.getBeansOfType(PaymentChannelClient.class)).hasSize(1);
            assertThat(context.getBeansOfType(PaymentChannelCallbackProtocol.class)).hasSize(1);
        });
    }

    @Test
    void shouldDisableAllMpgsProviderBeans() {
        contextRunner.withPropertyValues("payment.channel.mpgs.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(PaymentChannelAdapter.class));
    }
}
