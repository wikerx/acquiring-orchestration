package com.scott.payment.data.config.export;

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
 * Data 到 Admin 导出源内部客户端配置，同时支持固定地址和 Nacos 服务发现。
 */
@Configuration
@EnableConfigurationProperties(DataAdminClientProperties.class)
public class DataAdminClientConfiguration {

    /**
     * 创建无系统代理的固定地址客户端。
     */
    @Bean("dataAdminInternalRestTemplate")
    public RestTemplate direct(TraceIdRestTemplateCustomizer customizer,
                               DataAdminClientProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setProxy(Proxy.NO_PROXY);
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        return customizer.customize(new RestTemplate(factory));
    }

    /**
     * 创建通过服务发现解析 service-admin 的负载均衡客户端。
     */
    @Bean("dataAdminInternalLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate loadBalanced(RestTemplateBuilder builder,
                                     TraceIdRestTemplateInterceptor interceptor,
                                     DataAdminClientProperties properties) {
        return builder.connectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .readTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .additionalInterceptors(interceptor).build();
    }
}
