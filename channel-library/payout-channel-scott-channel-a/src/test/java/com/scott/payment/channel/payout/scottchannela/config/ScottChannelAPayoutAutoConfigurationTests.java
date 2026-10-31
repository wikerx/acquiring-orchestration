package com.scott.payment.channel.payout.scottchannela.config;

import com.scott.payment.channel.payout.api.PayoutChannelClient;
import com.scott.payment.channel.payout.scottchannela.adapter.ScottChannelAPayoutAdapter;
import com.scott.payment.channel.payout.scottchannela.client.PayoutApiClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 ScottChannelA 代付自动配置的装配条件、Bean 唯一名称和元数据入口。
 **/
class ScottChannelAPayoutAutoConfigurationTests {

    private static final String AUTO_CONFIGURATION_IMPORTS =
            "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ScottChannelAPayoutAutoConfiguration.class));

    @Test
    void shouldRegisterPayoutComponentsByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(PayoutApiClient.class);
            assertThat(context).hasSingleBean(ScottChannelAPayoutAdapter.class);
            assertThat(context.getBeansOfType(PayoutChannelClient.class)).hasSize(1);
            assertThat(context).hasBean("scottChannelAPayoutApiClient");
            assertThat(context).hasBean("scottChannelAPayoutAdapter");
        });
    }

    @Test
    void shouldDisablePayoutComponents() {
        contextRunner.withPropertyValues("payment.channel.scott-channel-a.payout.enabled=false")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(PayoutApiClient.class);
                    assertThat(context).doesNotHaveBean(ScottChannelAPayoutAdapter.class);
                });
    }

    @Test
    void shouldPublishMovedAutoConfigurationClassName() throws IOException {
        String imports = new ClassPathResource(AUTO_CONFIGURATION_IMPORTS)
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(imports.lines()).contains(
                "com.scott.payment.channel.payout.scottchannela.config.ScottChannelAPayoutAutoConfiguration");
    }
}
