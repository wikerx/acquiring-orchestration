package com.scott.payment.channel.payment.mpgs;

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
 * @classname : MpgsChannelAutoConfigurationTests
 * @date : 2026-08-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS 插件自动配置测试，验证兼容默认启用及显式关闭行为。
 * @status : create
 */
class MpgsChannelAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(MpgsChannelAutoConfiguration.class));

    @Test
    void shouldEnableMpgsProviderByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(MpgsPaymentChannelClient.class);
            assertThat(context).hasSingleBean(MpgsPaymentChannelCallbackHandler.class);
            assertThat(context).hasSingleBean(MpgsCallbackVerifier.class);
            assertThat(context.getBeansOfType(PaymentChannelClient.class)).hasSize(1);
            assertThat(context.getBeansOfType(PaymentChannelCallbackHandler.class)).hasSize(1);
            assertThat(context.getBeansOfType(PaymentChannelCallbackVerifier.class)).hasSize(1);
        });
    }

    @Test
    void shouldDisableAllMpgsProviderBeans() {
        contextRunner.withPropertyValues("payment.channel.mpgs.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(MpgsPaymentChannelClient.class));
    }
}
