package com.scott.payment.data.config;

import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.component.security.jwt.MerchantCallbackJwtSigner;
import com.scott.payment.component.web.internal.InternalHttpTimeoutPolicy;
import com.scott.payment.component.web.trace.TraceIdRestTemplateCustomizer;
import com.scott.payment.data.service.impl.MerchantCallbackDnsResolver;
import com.scott.payment.data.service.impl.MerchantCallbackTargetValidator;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DataMerchantNotificationConfig
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 商户通知 HTTP 配置，提供禁用系统代理且具有有界连接和读取超时的独立客户端
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(DataMerchantNotificationProperties.class)
public class DataMerchantNotificationConfig {

    /**
     * 提供商户回调正文加密组件。
     *
     * @return 无状态的 OpenAPI 载荷加密器
     */
    @Bean
    public OpenApiPayloadCrypto dataMerchantCallbackPayloadCrypto() {
        return new OpenApiPayloadCrypto();
    }

    /**
     * 提供平台回调 JWT 签发组件。
     *
     * @return 不持有商户密钥的 JWT 签发器
     */
    @Bean
    public MerchantCallbackJwtSigner merchantCallbackJwtSigner() {
        return new MerchantCallbackJwtSigner();
    }

    /**
     * 注册商户通知 HTTP 客户端。
     *
     * @param properties 商户通知执行参数
     * @param traceCustomizer traceId 请求头定制器
     * @param httpClient 已绑定公网 DNS 校验和禁止重定向策略的回调客户端
     * @return 仅供商户回调使用的 RestTemplate
     */
    @Bean("dataMerchantNotificationRestTemplate")
    public RestTemplate dataMerchantNotificationRestTemplate(DataMerchantNotificationProperties properties,
                                                              TraceIdRestTemplateCustomizer traceCustomizer,
                                                              @Qualifier("dataMerchantNotificationHttpClient")
                                                              CloseableHttpClient httpClient) {
        validateTimeouts(properties);
        HttpComponentsClientHttpRequestFactory requestFactory =
                new HttpComponentsClientHttpRequestFactory(httpClient);
        return traceCustomizer.customize(new RestTemplate(requestFactory));
    }

    /** 创建由 Spring 负责关闭的商户回调连接池客户端。 */
    @Bean(name = "dataMerchantNotificationHttpClient", destroyMethod = "close")
    public CloseableHttpClient dataMerchantNotificationHttpClient(
            MerchantCallbackTargetValidator targetValidator,
            DataMerchantNotificationProperties properties) {
        validateTimeouts(properties);
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(properties.getConnectTimeoutMillis()))
                .build();
        PoolingHttpClientConnectionManager connectionManager = PoolingHttpClientConnectionManagerBuilder.create()
                .setDnsResolver(new MerchantCallbackDnsResolver(targetValidator))
                .setDefaultConnectionConfig(connectionConfig)
                .build();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.ofMilliseconds(properties.getConnectTimeoutMillis()))
                .setResponseTimeout(Timeout.ofMilliseconds(properties.getReadTimeoutMillis()))
                .build();
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .disableRedirectHandling()
                .disableCookieManagement()
                .build();
    }

    /**
     * 校验回调超时和任务恢复窗口，防止恢复线程与仍在执行的 HTTP 请求并发发送同一通知。
     *
     * @param properties 商户通知执行参数
     */
    void validateTimeouts(DataMerchantNotificationProperties properties) {
        InternalHttpTimeoutPolicy.validate("data merchant-notification",
                properties.getConnectTimeoutMillis(), properties.getReadTimeoutMillis());
        long minimumProcessingTimeoutMillis = (long) properties.getConnectTimeoutMillis()
                + properties.getReadTimeoutMillis();
        if (properties.getProcessingTimeoutSeconds() <= 0
                || properties.getProcessingTimeoutSeconds() * 1_000L <= minimumProcessingTimeoutMillis) {
            throw new IllegalStateException("merchant notification processing timeout must exceed HTTP timeout");
        }
        if (properties.getRecoveryBatchLimit() <= 0 || properties.getRecoveryBatchLimit() > 500) {
            throw new IllegalStateException("merchant notification recovery batch limit must be between 1 and 500");
        }
        if (properties.getCallbackJwtTtlSeconds() <= 0 || properties.getCallbackJwtTtlSeconds() > 300) {
            throw new IllegalStateException("merchant callback JWT TTL must be between 1 and 300 seconds");
        }
        if (properties.getSecurityMaterialCacheTtl() == null
                || properties.getSecurityMaterialCacheTtl().isZero()
                || properties.getSecurityMaterialCacheTtl().isNegative()
                || properties.getSecurityMaterialCacheTtl().compareTo(java.time.Duration.ofMinutes(10)) > 0) {
            throw new IllegalStateException(
                    "merchant callback security material cache TTL must be between 1 nanosecond and 10 minutes");
        }
        if (properties.getSecurityMaterialCacheMaxEntries() <= 0
                || properties.getSecurityMaterialCacheMaxEntries() > 100_000) {
            throw new IllegalStateException(
                    "merchant callback security material cache max entries must be between 1 and 100000");
        }
    }
}
