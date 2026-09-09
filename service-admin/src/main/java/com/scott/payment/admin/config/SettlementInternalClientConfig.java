package com.scott.payment.admin.config;

import com.scott.payment.component.web.trace.TraceIdRestTemplateCustomizer;
import com.scott.payment.component.web.trace.TraceIdRestTemplateInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
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
 * @classname : SettlementInternalClientConfig
 * @date : 2026-08-26 21:20
 * @email : scott_x@163.com
 * @description : 结算管理内部客户端的直连与服务发现 RestTemplate 配置，统一传递 traceId 和有界超时。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(SettlementInternalClientProperties.class)
public class SettlementInternalClientConfig {

    /**
     * 构造用于 localhost 或 IP 地址的无代理直连客户端。
     *
     * @param customizer traceId 透传定制器
     * @param properties 已校验的服务根地址、固定身份和超时配置
     * @return 无代理直连内部客户端
     */
    @Bean("adminSettlementInternalRestTemplate")
    public RestTemplate direct(TraceIdRestTemplateCustomizer customizer,
                               SettlementInternalClientProperties properties) {
        properties.validate();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setProxy(Proxy.NO_PROXY);
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        return customizer.customize(new RestTemplate(factory));
    }

    /**
     * 构造用于 service-settlement 服务名解析的负载均衡客户端。
     *
     * @param builder Spring RestTemplate 构建器
     * @param interceptor traceId 透传拦截器
     * @param properties 已校验的服务根地址、固定身份和超时配置
     * @return 支持服务发现的内部客户端
     */
    @Bean("adminSettlementInternalLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate loadBalanced(RestTemplateBuilder builder,
                                     TraceIdRestTemplateInterceptor interceptor,
                                     SettlementInternalClientProperties properties) {
        properties.validate();
        return builder.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .additionalInterceptors(interceptor).build();
    }
}
