package com.scott.payment.payment.config;

import com.scott.payment.component.web.trace.TraceIdRestTemplateCustomizer;
import com.scott.payment.component.web.trace.TraceIdRestTemplateInterceptor;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * service-risk 内部调用配置，提供共享有界连接池以及直连、服务发现两类 RestTemplate。
 */
@Configuration
@EnableConfigurationProperties(RiskClientProperties.class)
public class RiskClientConfig {

    /**
     * 注册风控服务直连 RestTemplate。
     *
     * <p>直连与服务发现客户端共享同一个连接池，避免本地联调或显式地址场景重新退化为
     * 每次请求建立连接；Apache HttpClient 默认不读取系统代理，保持原有直连语义。</p>
     *
     * @param traceIdRestTemplateCustomizer traceId 传播定制器
     * @param properties 风控客户端配置
     * @param httpClient 风控专用 HTTP 客户端
     * @return 直连 RestTemplate
     */
    @Bean("riskRestTemplate")
    public RestTemplate riskRestTemplate(TraceIdRestTemplateCustomizer traceIdRestTemplateCustomizer,
                                         RiskClientProperties properties,
                                         @Qualifier("riskHttpClient") CloseableHttpClient httpClient) {
        properties.validate();
        return traceIdRestTemplateCustomizer.customize(
                new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient)));
    }

    /**
     * 创建 service-risk 专用连接池。
     *
     * <p>容量按实际下游实例路由限制，避免默认单路由连接数在并发交易下形成隐式排队。</p>
     *
     * @param properties 风控客户端容量、超时和连接生命周期配置
     * @return 由 Spring 负责关闭的风控内部连接池
     */
    @Bean(name = "riskHttpConnectionManager", destroyMethod = "close")
    public PoolingHttpClientConnectionManager riskHttpConnectionManager(RiskClientProperties properties) {
        properties.validate();
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofMilliseconds(properties.getConnectTimeoutMillis()))
                .setValidateAfterInactivity(
                        TimeValue.ofMilliseconds(properties.getValidateAfterInactivityMillis()))
                .setTimeToLive(TimeValue.ofMilliseconds(properties.getConnectionTimeToLiveMillis()))
                .build();
        return PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(properties.getMaxConnectionsTotal())
                .setMaxConnPerRoute(properties.getMaxConnectionsPerRoute())
                .setDefaultConnectionConfig(connectionConfig)
                .build();
    }

    /**
     * 创建 service-risk 专用 Apache HttpClient。
     *
     * <p>底层自动重试保持关闭，防止调用方无感知地重放风控评估或限额预占请求；
     * 连接获取、响应等待和空闲清理均使用显式有界配置。</p>
     *
     * @param connectionManager 风控内部连接池
     * @param properties 风控客户端超时和连接清理配置
     * @return 由 Spring 负责关闭的风控内部 HTTP 客户端
     */
    @Bean(name = "riskHttpClient", destroyMethod = "close")
    public CloseableHttpClient riskHttpClient(
            @Qualifier("riskHttpConnectionManager") PoolingHttpClientConnectionManager connectionManager,
            RiskClientProperties properties) {
        properties.validate();
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(
                        Timeout.ofMilliseconds(properties.getConnectionRequestTimeoutMillis()))
                .setResponseTimeout(Timeout.ofMilliseconds(properties.getReadTimeoutMillis()))
                .build();
        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setConnectionManagerShared(true)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofMilliseconds(properties.getIdleConnectionEvictMillis()))
                .disableAutomaticRetries()
                .disableCookieManagement()
                .build();
    }

    /**
     * 注册风控服务负载均衡 RestTemplate。
     *
     * @param builder Spring Boot 客户端构建器
     * @param traceInterceptor traceId 传播拦截器
     * @param properties 风控客户端配置
     * @param httpClient 风控专用 HTTP 客户端
     * @return 负载均衡 RestTemplate
     */
    @Bean("riskLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate riskLoadBalancedRestTemplate(RestTemplateBuilder builder,
                                                     TraceIdRestTemplateInterceptor traceInterceptor,
                                                     RiskClientProperties properties,
                                                     @Qualifier("riskHttpClient") CloseableHttpClient httpClient) {
        properties.validate();
        return builder
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient))
                .additionalInterceptors(traceInterceptor)
                .build();
    }
}
