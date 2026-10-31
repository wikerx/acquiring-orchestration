package com.scott.payment.channel.payout.scottchannela.config;

import com.scott.payment.channel.payout.scottchannela.adapter.ScottChannelAPayoutAdapter;
import com.scott.payment.channel.payout.scottchannela.client.PayoutApiClient;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 注册 ScottChannelA 代付 SPI 和 HTTP 客户端。
 **/
@AutoConfiguration
@EnableConfigurationProperties(ChannelProperties.class)
@ConditionalOnProperty(prefix = "payment.channel.scott-channel-a.payout", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ScottChannelAPayoutAutoConfiguration {

    /**
     * 创建 ScottChannelA 代付 HTTP 客户端。
     *
     * @param properties 环境级连接兜底配置
     * @return 代付 HTTP 客户端
     **/
    @Bean("scottChannelAPayoutApiClient")
    @ConditionalOnMissingBean
    PayoutApiClient scottChannelAPayoutApiClient(ChannelProperties properties) {
        return new PayoutApiClient(properties);
    }

    /**
     * 创建并注册 ScottChannelA 代付 SPI。
     *
     * @param apiClient 代付 HTTP 客户端
     * @return 代付 SPI 实现
     **/
    @Bean("scottChannelAPayoutAdapter")
    @ConditionalOnMissingBean
    ScottChannelAPayoutAdapter scottChannelAPayoutAdapter(PayoutApiClient apiClient) {
        return new ScottChannelAPayoutAdapter(apiClient);
    }
}
