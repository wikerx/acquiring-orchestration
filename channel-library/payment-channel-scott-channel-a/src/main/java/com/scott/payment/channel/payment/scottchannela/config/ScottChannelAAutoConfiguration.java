package com.scott.payment.channel.payment.scottchannela.config;

import com.scott.payment.channel.payment.scottchannela.adapter.ScottChannelAPaymentAdapter;
import com.scott.payment.channel.payment.scottchannela.callback.CallbackHandler;
import com.scott.payment.channel.payment.scottchannela.callback.CallbackVerifier;
import com.scott.payment.channel.payment.scottchannela.callback.ScottChannelACallbackProtocol;
import com.scott.payment.channel.payment.scottchannela.client.PaymentApiClient;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 注册 ScottChannelA 收单客户端和回调协议组件。
 **/
@AutoConfiguration
@EnableConfigurationProperties(ChannelProperties.class)
@ConditionalOnProperty(prefix = "payment.channel.scott-channel-a", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class ScottChannelAAutoConfiguration {

    /**
     * 创建 ScottChannelA 收单 HTTP 客户端。
     *
     * @param properties 环境级连接兜底配置
     * @return 收单 HTTP 客户端
     **/
    @Bean("scottChannelAPaymentApiClient")
    @ConditionalOnMissingBean
    PaymentApiClient scottChannelAPaymentApiClient(ChannelProperties properties) {
        return new PaymentApiClient(properties);
    }

    /**
     * 创建并注册 ScottChannelA 收单 SPI。
     *
     * @param apiClient 收单 HTTP 客户端
     * @return 收单 SPI 实现
     **/
    @Bean("scottChannelAPaymentAdapter")
    @ConditionalOnMissingBean
    ScottChannelAPaymentAdapter scottChannelAPaymentAdapter(PaymentApiClient apiClient) {
        return new ScottChannelAPaymentAdapter(apiClient);
    }

    /**
     * @return 基于原始请求字节的 ScottChannelA 回调验签器
     **/
    @Bean("scottChannelACallbackVerifier")
    @ConditionalOnMissingBean
    CallbackVerifier scottChannelACallbackVerifier() {
        return new CallbackVerifier();
    }

    /**
     * @return ScottChannelA 已验签回调解析器
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
    ScottChannelACallbackProtocol scottChannelACallbackProtocol(
            CallbackVerifier verifier,
            CallbackHandler parser) {
        return new ScottChannelACallbackProtocol(verifier, parser);
    }
}
