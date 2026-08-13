package com.scott.payment.channel.payment.worldpay;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : WorldPayChannelAutoConfiguration
 * @date : 2026-08-12 00:00
 * @description : Worldpay provider 自动配置，同时注册 WPGJSON/WPGXML 客户端、回调处理器和验签实现。
 * @status : create
 */
@AutoConfiguration
@EnableConfigurationProperties(WorldPayChannelProperties.class)
@ConditionalOnProperty(prefix = "payment.channel.worldpay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WorldPayChannelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    WorldPayTradeStatusMapper worldPayTradeStatusMapper() {
        return new WorldPayTradeStatusMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    WorldPayJsonRequestMapper worldPayJsonRequestMapper() {
        return new WorldPayJsonRequestMapper();
    }

    @Bean
    @ConditionalOnMissingBean
    WorldPayJsonResponseMapper worldPayJsonResponseMapper(WorldPayTradeStatusMapper tradeStatusMapper) {
        return new WorldPayJsonResponseMapper(tradeStatusMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    WorldPayJsonApiClient worldPayJsonApiClient(WorldPayJsonRequestMapper requestMapper,
                                                WorldPayJsonResponseMapper responseMapper) {
        return new WorldPayJsonApiClient(requestMapper, responseMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    WorldPayJsonPaymentChannelClient worldPayJsonPaymentChannelClient(WorldPayJsonApiClient apiClient) {
        return new WorldPayJsonPaymentChannelClient(apiClient);
    }

    @Bean
    @ConditionalOnMissingBean
    WorldPayJsonCallbackHandler worldPayJsonCallbackHandler() {
        return new WorldPayJsonCallbackHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    WorldPayXmlCodec worldPayXmlCodec() {
        return new WorldPayXmlCodec();
    }

    @Bean
    @ConditionalOnMissingBean
    WorldPayXmlRequestMapper worldPayXmlRequestMapper(WorldPayXmlCodec codec) {
        return new WorldPayXmlRequestMapper(codec);
    }

    @Bean
    @ConditionalOnMissingBean
    WorldPayXmlResponseMapper worldPayXmlResponseMapper(WorldPayXmlCodec codec,
                                                        WorldPayTradeStatusMapper tradeStatusMapper) {
        return new WorldPayXmlResponseMapper(codec, tradeStatusMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    WorldPayXmlApiClient worldPayXmlApiClient(WorldPayXmlRequestMapper requestMapper,
                                              WorldPayXmlResponseMapper responseMapper) {
        return new WorldPayXmlApiClient(requestMapper, responseMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    WorldPayXmlPaymentChannelClient worldPayXmlPaymentChannelClient(WorldPayXmlApiClient apiClient) {
        return new WorldPayXmlPaymentChannelClient(apiClient);
    }

    @Bean
    @ConditionalOnMissingBean
    WorldPayXmlCallbackHandler worldPayXmlCallbackHandler() {
        return new WorldPayXmlCallbackHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    WorldPayCallbackVerifier worldPayCallbackVerifier() {
        return new WorldPayCallbackVerifier();
    }
}
