package com.scott.payment.channel.payment.mpgs;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MpgsChannelAutoConfiguration
 * @date : 2026-08-12 00:00
 * @email : scott_x@163.com
 * @description : MPGS provider 自动配置，仅在 payment.channel.mpgs.enabled=true 时注册该渠道的客户端、映射器、回调和验签实现。
 * @status : create
 */
@AutoConfiguration
@EnableConfigurationProperties(MpgsChannelProperties.class)
@ConditionalOnProperty(prefix = "payment.channel.mpgs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class MpgsChannelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    MpgsTradeStatusMapper mpgsTradeStatusMapper() {
        return new MpgsTradeStatusMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    MpgsErrorCodeMapper mpgsErrorCodeMapper() {
        return new MpgsErrorCodeMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    MpgsRequestMapper mpgsRequestMapper() {
        return new MpgsRequestMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    MpgsResponseMapper mpgsResponseMapper(MpgsTradeStatusMapper tradeStatusMapper,
                                          MpgsErrorCodeMapper errorCodeMapper) {
        return new MpgsResponseMapper(tradeStatusMapper, errorCodeMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    MpgsApiClient mpgsApiClient(MpgsChannelProperties properties,
                                MpgsRequestMapper requestMapper,
                                MpgsResponseMapper responseMapper) {
        return new MpgsApiClient(properties, requestMapper, responseMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    MpgsPaymentChannelClient mpgsPaymentChannelClient(MpgsApiClient apiClient) {
        return new MpgsPaymentChannelClient(apiClient);
    }

    @Bean
    @ConditionalOnMissingBean
    MpgsPaymentChannelCallbackHandler mpgsPaymentChannelCallbackHandler(MpgsTradeStatusMapper tradeStatusMapper,
                                                                        MpgsErrorCodeMapper errorCodeMapper) {
        return new MpgsPaymentChannelCallbackHandler(tradeStatusMapper, errorCodeMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    MpgsCallbackVerifier mpgsCallbackVerifier() {
        return new MpgsCallbackVerifier();
    }
}
