package com.scott.payment.job.config;

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
 * service-admin 内部客户端配置，提供直连和 Nacos 服务发现两类有界超时客户端。
 */
@Configuration
@EnableConfigurationProperties(AdminInternalClientProperties.class)
public class AdminInternalClientConfig {

    /**
     * 注册禁用系统代理的直连客户端。
     *
     * @param traceCustomizer traceId 请求头定制器
     * @param properties 客户端配置
     * @return service-admin 直连客户端
     */
    @Bean("jobAdminInternalRestTemplate")
    public RestTemplate jobAdminInternalRestTemplate(TraceIdRestTemplateCustomizer traceCustomizer,
                                                      AdminInternalClientProperties properties) {
        properties.validate();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setProxy(Proxy.NO_PROXY);
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMillis());
        requestFactory.setReadTimeout(properties.getReadTimeoutMillis());
        return traceCustomizer.customize(new RestTemplate(requestFactory));
    }

    /**
     * 注册支持 Nacos 服务发现的负载均衡客户端。
     *
     * @param restTemplateBuilder RestTemplate 构造器
     * @param traceInterceptor traceId 请求拦截器
     * @param properties 客户端配置
     * @return service-admin 服务发现客户端
     */
    @Bean("jobAdminInternalLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate jobAdminInternalLoadBalancedRestTemplate(RestTemplateBuilder restTemplateBuilder,
                                                                 TraceIdRestTemplateInterceptor traceInterceptor,
                                                                 AdminInternalClientProperties properties) {
        properties.validate();
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .additionalInterceptors(traceInterceptor)
                .build();
    }
}
