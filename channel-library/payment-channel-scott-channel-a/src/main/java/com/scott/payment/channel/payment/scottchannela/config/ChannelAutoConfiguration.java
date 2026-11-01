package com.scott.payment.channel.payment.scottchannela.config;

import com.scott.payment.channel.payment.scottchannela.adapter.PaymentChannelAdapter;
import com.scott.payment.channel.payment.scottchannela.callback.CallbackHandler;
import com.scott.payment.channel.payment.scottchannela.callback.CallbackVerifier;
import com.scott.payment.channel.payment.scottchannela.callback.CallbackProtocol;
import com.scott.payment.channel.payment.scottchannela.client.PaymentApiClient;
import com.scott.payment.component.core.security.OutboundHttpUrlPolicy;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * 注册 ScottChannelA 收单客户端和回调协议组件。
 **/
@AutoConfiguration
@EnableConfigurationProperties(ChannelProperties.class)
@ConditionalOnProperty(prefix = "payment.channel.scott-channel-a", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ChannelAutoConfiguration {

    /**
     * 创建 ScottChannelA 收单 HTTP 客户端。
     *
     * @param properties 环境级连接兜底配置
     * @param environment Spring 活动环境
     * @return 收单 HTTP 客户端
     **/
    @Bean("scottChannelAPaymentApiClient")
    @ConditionalOnMissingBean
    PaymentApiClient scottChannelAPaymentApiClient(ChannelProperties properties, Environment environment) {
        return new PaymentApiClient(properties, new OutboundHttpUrlPolicy(
                OutboundHttpUrlPolicy.modeForProfiles(environment.getActiveProfiles())));
    }

    /**
     * 创建并注册 ScottChannelA 收单 SPI。
     *
     * @param apiClient 收单 HTTP 客户端
     * @return 收单 SPI 实现
     **/
    @Bean("scottChannelAPaymentAdapter")
    @ConditionalOnMissingBean
    PaymentChannelAdapter scottChannelAPaymentAdapter(PaymentApiClient apiClient) {
        return new PaymentChannelAdapter(apiClient);
    }

    /**
     * 创建基于原始请求字节的 ScottChannelA 回调验签器。
     *
     * @return 回调验签器
     **/
    @Bean("scottChannelACallbackVerifier")
    @ConditionalOnMissingBean
    CallbackVerifier scottChannelACallbackVerifier() {
        return new CallbackVerifier();
    }

    /**
     * 创建 ScottChannelA 已验签回调解析器。
     *
     * @return 回调解析器
     **/
    @Bean("scottChannelACallbackHandler")
    @ConditionalOnMissingBean
    CallbackHandler scottChannelACallbackHandler() {
        return new CallbackHandler();
    }

    /**
     * 组合 ScottChannelA 回调协议组件。
     *
     * @param verifier 原始请求体验签器
     * @param parser 已验签回调解析器
     * @return 回调协议注册项
     **/
    @Bean("scottChannelACallbackProtocol")
    @ConditionalOnMissingBean
    CallbackProtocol scottChannelACallbackProtocol(
            CallbackVerifier verifier,
            CallbackHandler parser) {
        return new CallbackProtocol(verifier, parser);
    }
}
