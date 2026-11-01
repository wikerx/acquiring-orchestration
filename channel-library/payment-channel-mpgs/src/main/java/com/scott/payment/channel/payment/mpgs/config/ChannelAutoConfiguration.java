package com.scott.payment.channel.payment.mpgs.config;

import com.scott.payment.channel.payment.mpgs.adapter.PaymentChannelAdapter;
import com.scott.payment.channel.payment.mpgs.callback.CallbackHandler;
import com.scott.payment.channel.payment.mpgs.callback.CallbackProtocol;
import com.scott.payment.channel.payment.mpgs.callback.CallbackVerifier;
import com.scott.payment.channel.payment.mpgs.client.ApiClient;
import com.scott.payment.channel.payment.mpgs.converter.RequestConverter;
import com.scott.payment.channel.payment.mpgs.converter.ResponseCodeMapper;
import com.scott.payment.channel.payment.mpgs.converter.ResponseConverter;
import com.scott.payment.channel.payment.mpgs.converter.TradeStatusMapper;
import com.scott.payment.component.core.security.OutboundHttpUrlPolicy;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

/**
 * MPGS provider 自动配置，仅在 payment.channel.mpgs.enabled=true 时注册该渠道的客户端、映射器、回调和验签实现。
 */
@AutoConfiguration
@EnableConfigurationProperties(ChannelProperties.class)
@ConditionalOnProperty(prefix = "payment.channel.mpgs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ChannelAutoConfiguration {

    /**
     * 注册 MPGS 交易状态映射器。
     *
     * @return MPGS 交易状态映射器
     */
    @Bean
    @ConditionalOnMissingBean
    TradeStatusMapper mpgsTradeStatusMapper() {
        return new TradeStatusMapper();
    }

    /**
     * 注册 MPGS 响应码映射器。
     *
     * @return MPGS 响应码映射器
     */
    @Bean
    @ConditionalOnMissingBean
    ResponseCodeMapper mpgsErrorCodeMapper() {
        return new ResponseCodeMapper();
    }

    /**
     * 注册平台请求到 MPGS 请求对象的转换器。
     *
     * @return MPGS 请求转换器
     */
    @Bean
    @ConditionalOnMissingBean
    RequestConverter mpgsRequestMapper() {
        return new RequestConverter();
    }

    /**
     * 注册 MPGS 响应转换器并复用统一状态、响应码规则。
     *
     * @param tradeStatusMapper 交易状态映射器
     * @param responseCodeMapper 响应码映射器
     * @return MPGS 响应转换器
     */
    @Bean
    @ConditionalOnMissingBean
    ResponseConverter mpgsResponseMapper(TradeStatusMapper tradeStatusMapper,
                                          ResponseCodeMapper responseCodeMapper) {
        return new ResponseConverter(tradeStatusMapper, responseCodeMapper);
    }

    /**
     * 注册负责认证、HTTP 调用和安全审计的 MPGS API 客户端。
     *
     * @param properties MPGS 静态配置
     * @param requestConverter 请求转换器
     * @param responseConverter 响应转换器
     * @param environment Spring 活动环境
     * @return MPGS API 客户端
     */
    @Bean
    @ConditionalOnMissingBean
    ApiClient mpgsApiClient(ChannelProperties properties,
                            RequestConverter requestConverter,
                            ResponseConverter responseConverter,
                            Environment environment) {
        return new ApiClient(properties, requestConverter, responseConverter, new OutboundHttpUrlPolicy(
                OutboundHttpUrlPolicy.modeForProfiles(environment.getActiveProfiles())));
    }

    /**
     * 注册平台支付渠道 SPI 的 MPGS 适配器。
     *
     * @param apiClient MPGS API 客户端
     * @return MPGS 支付渠道适配器
     */
    @Bean
    @ConditionalOnMissingBean
    PaymentChannelAdapter mpgsPaymentChannelClient(ApiClient apiClient) {
        return new PaymentChannelAdapter(apiClient);
    }

    /**
     * 注册已验签 MPGS 回调的纯解析器。
     *
     * @param tradeStatusMapper 交易状态映射器
     * @param responseCodeMapper 响应码映射器
     * @return MPGS 回调解析器
     */
    @Bean
    @ConditionalOnMissingBean
    CallbackHandler mpgsPaymentChannelCallbackHandler(TradeStatusMapper tradeStatusMapper,
                                                                        ResponseCodeMapper responseCodeMapper) {
        return new CallbackHandler(tradeStatusMapper, responseCodeMapper);
    }

    /**
     * 注册 MPGS Notification Secret 验签器。
     *
     * @return MPGS 回调验签器
     */
    @Bean
    @ConditionalOnMissingBean
    CallbackVerifier mpgsCallbackVerifier() {
        return new CallbackVerifier();
    }

    /**
     * 注册组合验签和解析步骤的 MPGS 回调协议。
     *
     * @param verifier MPGS 回调验签器
     * @param parser MPGS 回调解析器
     * @return MPGS 回调协议
     */
    @Bean
    @ConditionalOnMissingBean
    CallbackProtocol mpgsPaymentChannelCallbackProtocol(
            CallbackVerifier verifier,
            CallbackHandler parser) {
        return new CallbackProtocol(verifier, parser);
    }
}
