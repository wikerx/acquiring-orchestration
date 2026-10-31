package com.scott.payment.openapi.config;

import com.scott.payment.component.web.trace.TraceIdRestTemplateCustomizer;
import com.scott.payment.component.web.trace.TraceIdRestTemplateInterceptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.Proxy;
import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentClientConfig
 * @date : 2026-05-31 21:52
 * @email : scott_x@163.com
 * @description : 配置 service-openapi 调用 service-payment 的直连与服务发现客户端，统一校验超时边界并传播 traceId。
 * @status : create
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
     * 注册带服务发现能力的 RestTemplate，并将连接、读取超时同时应用到实际请求工厂。
     * <p>
     * dev、test、uat、prod 环境默认使用 `http://service-payment/...`，由 Nacos 服务发现和
     * Spring Cloud LoadBalancer 选择可用实例。
     *
     * @param builder Spring Boot 客户端构建器
     * @param traceInterceptor traceId 传播拦截器
     * @param properties 支付内部客户端地址与超时配置
     * @return 供 Nacos 服务名调用使用的负载均衡客户端
     */
    @Bean("paymentLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate paymentLoadBalancedRestTemplate(RestTemplateBuilder builder,
                                                        TraceIdRestTemplateInterceptor traceInterceptor,
                                                        PaymentClientProperties properties) {
        properties.validate();
        return builder
                .connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .additionalInterceptors(traceInterceptor)
                .build();
    }
}
