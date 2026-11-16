package com.scott.payment.merchant.config;

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
 * Merchant 到 Data 异步导出内部客户端配置，同时支持固定地址和 Nacos 服务发现。
 */
@Configuration
@EnableConfigurationProperties(DataExportInternalClientProperties.class)
public class DataExportInternalClientConfiguration {

    /**
     * 创建禁用系统代理的固定地址客户端。
     */
    @Bean("merchantDataExportRestTemplate")
    public RestTemplate direct(TraceIdRestTemplateCustomizer customizer,
                               DataExportInternalClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setProxy(Proxy.NO_PROXY);
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        return customizer.customize(new RestTemplate(factory));
    }

    /**
     * 创建通过服务发现解析 service-data 的负载均衡客户端。
     */
    @Bean("merchantDataExportLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate loadBalanced(RestTemplateBuilder builder,
                                     TraceIdRestTemplateInterceptor interceptor,
                                     DataExportInternalClientProperties properties) {
        return builder.connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .additionalInterceptors(interceptor)
                .build();
    }
}
