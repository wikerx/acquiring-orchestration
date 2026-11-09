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
 * Admin 到 Data 异步导出内部客户端配置，同时支持固定地址和 Nacos 服务发现。
 */
@Configuration
@EnableConfigurationProperties(DataExportClientProperties.class)
public class DataExportClientConfiguration {

    /**
     * 创建禁用系统代理的固定地址客户端。
     */
    @Bean("adminDataExportRestTemplate")
    public RestTemplate direct(TraceIdRestTemplateCustomizer customizer,
                               DataExportClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setProxy(Proxy.NO_PROXY);
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        return customizer.customize(new RestTemplate(factory));
    }

    /**
     * 创建通过服务发现解析 service-data 的负载均衡客户端。
     */
    @Bean("adminDataExportLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate loadBalanced(RestTemplateBuilder builder,
                                     TraceIdRestTemplateInterceptor interceptor,
                                     DataExportClientProperties properties) {
        return builder.connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .additionalInterceptors(interceptor)
                .build();
    }
}
