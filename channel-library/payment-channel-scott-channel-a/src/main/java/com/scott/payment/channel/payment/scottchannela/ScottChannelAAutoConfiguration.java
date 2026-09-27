package com.scott.payment.channel.payment.scottchannela;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ScottChannelAAutoConfiguration
 * @date : 2026-09-19 00:00
 * @email : scott_x@163.com
 * @description : ScottChannelA provider 自动配置，仅负责注册收单客户端、回调解析器和 raw-body HMAC 验签器。
 * @status : create
 */
@AutoConfiguration
@EnableConfigurationProperties(ScottChannelAProperties.class)
@ConditionalOnProperty(prefix = "payment.channel.scott-channel-a", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ScottChannelAAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    ScottChannelAApiClient scottChannelAApiClient(ScottChannelAProperties properties) {
        return new ScottChannelAApiClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    ScottChannelAPaymentChannelClient scottChannelAPaymentChannelClient(ScottChannelAApiClient apiClient) {
        return new ScottChannelAPaymentChannelClient(apiClient);
    }

    @Bean
    @ConditionalOnMissingBean
    ScottChannelACallbackVerifier scottChannelACallbackVerifier() {
        return new ScottChannelACallbackVerifier();
    }

    @Bean
    @ConditionalOnMissingBean
    ScottChannelACallbackHandler scottChannelACallbackHandler() {
        return new ScottChannelACallbackHandler();
    }
}
