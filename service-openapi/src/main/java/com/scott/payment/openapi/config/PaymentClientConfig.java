package com.scott.payment.openapi.config;

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
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.Proxy;

/**
 * 配置 service-openapi 调用 service-payment 的直连与服务发现客户端，统一校验超时边界并传播 traceId。
 */
@Configuration
@EnableConfigurationProperties({PaymentClientProperties.class, HostedCheckoutProperties.class})
public class PaymentClientConfig {

    /**
     * 注册使用显式主机地址的直连 RestTemplate，并绕过系统代理。
     * <p>
     * 本地联调如果配置 `http://127.0.0.1:port/...` 或 `http://localhost:port/...`，
     * 必须使用直连客户端，避免 Spring Cloud LoadBalancer 把 IP 当作服务名解析。
     *
     * @param traceIdRestTemplateCustomizer traceId 传播定制器
     * @param properties 支付内部客户端地址与超时配置
     * @return 供本地直连或显式 URL 调用使用的客户端
     */
    @Bean("paymentRestTemplate")
    public RestTemplate paymentRestTemplate(TraceIdRestTemplateCustomizer traceIdRestTemplateCustomizer,
                                            PaymentClientProperties properties) {
        properties.validate();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setProxy(Proxy.NO_PROXY);
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMillis());
        requestFactory.setReadTimeout(properties.getReadTimeoutMillis());
        return traceIdRestTemplateCustomizer.customize(new RestTemplate(requestFactory));
    }

    /**
     * 创建 service-payment 专用连接池。
     * <p>
     * 连接池容量按实际下游实例路由控制，避免 Apache HttpClient 默认单路由连接数在并发交易下形成排队瓶颈。
     *
     * @param properties 支付内部客户端容量、超时和连接生命周期配置
     * @return 由 Spring 负责关闭的支付内部连接池
     */
    @Bean(name = "paymentHttpConnectionManager", destroyMethod = "close")
    public PoolingHttpClientConnectionManager paymentHttpConnectionManager(PaymentClientProperties properties) {
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
     * 创建 service-payment 专用 Apache HttpClient。
     * <p>
     * 自动重试保持关闭，避免底层在业务调用方无感知时重放支付请求；空闲和过期连接由客户端后台清理。
     *
     * @param connectionManager 支付内部连接池
     * @param properties 支付内部客户端超时和清理配置
     * @return 由 Spring 负责关闭的支付内部 HTTP 客户端
     */
    @Bean(name = "paymentHttpClient", destroyMethod = "close")
    public CloseableHttpClient paymentHttpClient(
            @Qualifier("paymentHttpConnectionManager") PoolingHttpClientConnectionManager connectionManager,
            PaymentClientProperties properties) {
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
     * 注册带服务发现能力的 RestTemplate，并将连接、读取超时同时应用到实际请求工厂。
     * <p>
     * dev、test、uat、prod 环境默认使用 `http://service-payment/...`，由 Nacos 服务发现和
     * Spring Cloud LoadBalancer 选择可用实例。
     *
     * @param builder Spring Boot 客户端构建器
     * @param traceInterceptor traceId 传播拦截器
     * @param properties 支付内部客户端地址、超时与连接池配置
     * @param httpClient 支付核心专用连接池客户端
     * @return 供 Nacos 服务名调用使用的负载均衡客户端
     */
    @Bean("paymentLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate paymentLoadBalancedRestTemplate(RestTemplateBuilder builder,
                                                        TraceIdRestTemplateInterceptor traceInterceptor,
                                                        PaymentClientProperties properties,
                                                        @Qualifier("paymentHttpClient") CloseableHttpClient httpClient) {
        properties.validate();
        return builder
                .requestFactory(() -> new HttpComponentsClientHttpRequestFactory(httpClient))
                .additionalInterceptors(traceInterceptor)
                .build();
    }
}
