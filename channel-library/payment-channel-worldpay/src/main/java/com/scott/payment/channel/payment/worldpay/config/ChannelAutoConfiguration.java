package com.scott.payment.channel.payment.worldpay.config;

import com.scott.payment.channel.payment.worldpay.callback.CallbackProtocol;
import com.scott.payment.channel.payment.worldpay.converter.TradeStatusMapper;
import com.scott.payment.channel.payment.worldpay.xml.codec.XmlCodec;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 注册 Worldpay JSON、XML 渠道适配器及统一回调协议。
 *
 * <p>Bean 方法名称属于现有 Spring 装配契约，重构内部类型时保持不变。</p>
 */
@AutoConfiguration
@EnableConfigurationProperties(ChannelProperties.class)
@ConditionalOnProperty(prefix = "payment.channel.worldpay", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChannelAutoConfiguration {

    /**
     * 注册 JSON 与 XML 协议共用的 Worldpay 状态映射器。
     *
     * @return Worldpay 状态映射器
     */
    @Bean
    @ConditionalOnMissingBean
    TradeStatusMapper worldPayTradeStatusMapper() {
        return new TradeStatusMapper();
    }

    /**
     * 注册 Worldpay JSON 请求转换器。
     *
     * @return JSON 请求转换器
     */
    @Bean
    @ConditionalOnMissingBean
    com.scott.payment.channel.payment.worldpay.json.converter.RequestConverter worldPayJsonRequestMapper() {
        return new com.scott.payment.channel.payment.worldpay.json.converter.RequestConverter();
    }

    /**
     * 注册 Worldpay JSON 响应转换器。
     *
     * @param tradeStatusMapper 共用状态映射器
     * @return JSON 响应转换器
     */
    @Bean
    @ConditionalOnMissingBean
    com.scott.payment.channel.payment.worldpay.json.converter.ResponseConverter worldPayJsonResponseMapper(TradeStatusMapper tradeStatusMapper) {
        return new com.scott.payment.channel.payment.worldpay.json.converter.ResponseConverter(tradeStatusMapper);
    }

    /**
     * 注册 Worldpay JSON HTTP 客户端。
     *
     * @param requestConverter JSON 请求转换器
     * @param responseConverter JSON 响应转换器
     * @return JSON API 客户端
     */
    @Bean
    @ConditionalOnMissingBean
    com.scott.payment.channel.payment.worldpay.json.client.ApiClient worldPayJsonApiClient(com.scott.payment.channel.payment.worldpay.json.converter.RequestConverter requestConverter,
                                                com.scott.payment.channel.payment.worldpay.json.converter.ResponseConverter responseConverter) {
        return new com.scott.payment.channel.payment.worldpay.json.client.ApiClient(requestConverter, responseConverter);
    }

    /**
     * 注册 WPGJSON 支付渠道 SPI 适配器。
     *
     * @param apiClient JSON API 客户端
     * @return WPGJSON 支付渠道适配器
     */
    @Bean
    @ConditionalOnMissingBean
    com.scott.payment.channel.payment.worldpay.json.adapter.PaymentChannelAdapter worldPayJsonPaymentChannelClient(com.scott.payment.channel.payment.worldpay.json.client.ApiClient apiClient) {
        return new com.scott.payment.channel.payment.worldpay.json.adapter.PaymentChannelAdapter(apiClient);
    }

    /**
     * 注册启用安全 XML 特性的 Worldpay XML 编解码器。
     *
     * @return XML 编解码器
     */
    @Bean
    @ConditionalOnMissingBean
    XmlCodec worldPayXmlCodec() {
        return new XmlCodec();
    }

    /**
     * 注册 Worldpay XML 请求转换器。
     *
     * @param codec XML 编解码器
     * @return XML 请求转换器
     */
    @Bean
    @ConditionalOnMissingBean
    com.scott.payment.channel.payment.worldpay.xml.converter.RequestConverter worldPayXmlRequestMapper(XmlCodec codec) {
        return new com.scott.payment.channel.payment.worldpay.xml.converter.RequestConverter(codec);
    }

    /**
     * 注册 Worldpay XML 响应转换器。
     *
     * @param codec XML 编解码器
     * @param tradeStatusMapper 共用状态映射器
     * @return XML 响应转换器
     */
    @Bean
    @ConditionalOnMissingBean
    com.scott.payment.channel.payment.worldpay.xml.converter.ResponseConverter worldPayXmlResponseMapper(XmlCodec codec,
                                                        TradeStatusMapper tradeStatusMapper) {
        return new com.scott.payment.channel.payment.worldpay.xml.converter.ResponseConverter(codec, tradeStatusMapper);
    }

    /**
     * 注册 Worldpay XML HTTP 客户端。
     *
     * @param requestConverter XML 请求转换器
     * @param responseConverter XML 响应转换器
     * @return XML API 客户端
     */
    @Bean
    @ConditionalOnMissingBean
    com.scott.payment.channel.payment.worldpay.xml.client.ApiClient worldPayXmlApiClient(com.scott.payment.channel.payment.worldpay.xml.converter.RequestConverter requestConverter,
                                              com.scott.payment.channel.payment.worldpay.xml.converter.ResponseConverter responseConverter) {
        return new com.scott.payment.channel.payment.worldpay.xml.client.ApiClient(requestConverter, responseConverter);
    }

    /**
     * 注册 WPGXML 支付渠道 SPI 适配器。
     *
     * @param apiClient XML API 客户端
     * @return WPGXML 支付渠道适配器
     */
    @Bean
    @ConditionalOnMissingBean
    com.scott.payment.channel.payment.worldpay.xml.adapter.PaymentChannelAdapter worldPayXmlPaymentChannelClient(com.scott.payment.channel.payment.worldpay.xml.client.ApiClient apiClient) {
        return new com.scott.payment.channel.payment.worldpay.xml.adapter.PaymentChannelAdapter(apiClient);
    }

    /**
     * 注册在官方认证协议明确前保持失败关闭的 Worldpay 回调协议。
     *
     * @return Worldpay 回调协议
     */
    @Bean
    @ConditionalOnMissingBean
    CallbackProtocol worldPayPaymentChannelCallbackProtocol() {
        return new CallbackProtocol();
    }
}
