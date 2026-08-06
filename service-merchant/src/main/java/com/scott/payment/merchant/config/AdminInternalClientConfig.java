package com.scott.payment.merchant.config;

import com.scott.payment.component.web.trace.TraceIdRestTemplateInterceptor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminInternalClientConfig
 * @date : 2026-08-06 00:00
 * @description : service-admin 内部客户端配置，通过服务发现调用管理系统访问配置接口。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(AdminInternalClientProperties.class)
public class AdminInternalClientConfig {

    /**
     * 注册带链路标识和负载均衡能力的内部 RestTemplate。
     *
     * @param builder          Spring RestTemplate 构建器
     * @param traceInterceptor 链路标识拦截器
     * @return service-admin 内部调用客户端
     */
    @Bean("merchantAdminInternalRestTemplate")
    @LoadBalanced
    public RestTemplate merchantAdminInternalRestTemplate(RestTemplateBuilder builder,
                                                           TraceIdRestTemplateInterceptor traceInterceptor) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(3))
                .setReadTimeout(Duration.ofSeconds(10))
                .additionalInterceptors(traceInterceptor)
                .build();
    }
}
