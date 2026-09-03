package com.scott.payment.channel.payment.worldpay;

import com.scott.payment.channel.payment.api.PaymentChannelCallbackVerifier;
import com.scott.payment.channel.payment.api.PaymentChannelCallbackHandler;
import com.scott.payment.channel.payment.api.PaymentChannelClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayChannelAutoConfigurationTests
 * @date : 2026-08-12 00:00
 * @email : scott_x@163.com
 * @description : Worldpay 插件自动配置测试，验证 JSON/XML Provider 成组启用和关闭。
 * @status : create
 */
class WorldPayChannelAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(WorldPayChannelAutoConfiguration.class));

    @Test
    void shouldEnableWorldPayProviderByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(WorldPayJsonPaymentChannelClient.class);
            assertThat(context).hasSingleBean(WorldPayXmlPaymentChannelClient.class);
            assertThat(context).hasSingleBean(WorldPayCallbackVerifier.class);
            assertThat(context.getBeansOfType(PaymentChannelClient.class)).hasSize(2);
            assertThat(context.getBeansOfType(PaymentChannelCallbackHandler.class)).hasSize(2);
            assertThat(context.getBeansOfType(PaymentChannelCallbackVerifier.class)).hasSize(1);
        });
    }

    @Test
    void shouldDisableAllWorldPayProviderBeans() {
        contextRunner.withPropertyValues("payment.channel.worldpay.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(WorldPayJsonPaymentChannelClient.class));
    }
}
