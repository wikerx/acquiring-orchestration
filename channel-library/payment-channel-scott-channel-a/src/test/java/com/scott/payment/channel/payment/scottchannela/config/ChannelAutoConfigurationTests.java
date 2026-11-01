package com.scott.payment.channel.payment.scottchannela.config;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackProtocol;
import com.scott.payment.channel.payment.api.PaymentChannelClient;
import com.scott.payment.channel.payment.scottchannela.adapter.PaymentChannelAdapter;
import com.scott.payment.channel.payment.scottchannela.callback.CallbackHandler;
import com.scott.payment.channel.payment.scottchannela.callback.CallbackVerifier;
import com.scott.payment.channel.payment.scottchannela.callback.CallbackProtocol;
import com.scott.payment.channel.payment.scottchannela.client.PaymentApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 ScottChannelA 收单自动配置的装配条件、Bean 唯一名称和元数据入口。
 **/
class ChannelAutoConfigurationTests {

    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ChannelAutoConfiguration.class));

    @Test
    void shouldRegisterPaymentAndCallbackComponentsByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PaymentApiClient.class);
            assertThat(context).hasSingleBean(PaymentChannelAdapter.class);
            assertThat(context).hasSingleBean(CallbackVerifier.class);
            assertThat(context).hasSingleBean(CallbackHandler.class);
            assertThat(context).hasSingleBean(CallbackProtocol.class);
            assertThat(context.getBeansOfType(PaymentChannelClient.class)).hasSize(1);
            assertThat(context.getBeansOfType(PaymentChannelCallbackProtocol.class)).hasSize(1);
            assertThat(context).hasBean("scottChannelAPaymentApiClient");
            assertThat(context).hasBean("scottChannelAPaymentAdapter");
            assertThat(context).hasBean("scottChannelACallbackProtocol");
        });
    }

    @Test
    void shouldDisablePaymentAndCallbackComponents() {
        contextRunner.withPropertyValues("payment.channel.scott-channel-a.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(PaymentApiClient.class);
                    assertThat(context).doesNotHaveBean(PaymentChannelAdapter.class);
                    assertThat(context).doesNotHaveBean(CallbackProtocol.class);
                });
    }

    @Test
    void shouldPublishMovedAutoConfigurationClassName() throws IOException {
        String imports = new ClassPathResource(AUTO_CONFIGURATION_IMPORTS)
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(imports.lines()).contains(
                "com.scott.payment.channel.payment.scottchannela.config.ChannelAutoConfiguration");
    }
}
