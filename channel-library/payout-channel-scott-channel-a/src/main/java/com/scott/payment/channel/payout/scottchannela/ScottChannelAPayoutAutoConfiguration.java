package com.scott.payment.channel.payout.scottchannela;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ScottChannelAPayoutAutoConfiguration
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : ScottChannelA 代付 Provider 自动配置，仅负责注册 SPI 实现和 HTTP 客户端。
 * @status : create
 */
@AutoConfiguration
@EnableConfigurationProperties(ScottChannelAPayoutProperties.class)
@ConditionalOnProperty(prefix = "payment.channel.scott-channel-a.payout", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScottChannelAPayoutAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ScottChannelAPayoutApiClient scottChannelAPayoutApiClient(ScottChannelAPayoutProperties properties) {
        return new ScottChannelAPayoutApiClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    ScottChannelAPayoutClient scottChannelAPayoutClient(ScottChannelAPayoutApiClient apiClient) {
        return new ScottChannelAPayoutClient(apiClient);
    }
}
