package com.scott.payment.openapi.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutClientConfig
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 调用代付核心的 REST 客户端配置，区分本地直连和 Nacos 服务发现两种内部调用方式。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(PayoutClientProperties.class)
public class PayoutClientConfig {

    /**
     * 注册直连 RestTemplate。
     *
     * @return RestTemplate
     */
    @Bean("payoutRestTemplate")
    public RestTemplate payoutRestTemplate() {
        return new RestTemplate();
    }

    /**
     * 注册带负载均衡能力的 RestTemplate。
     *
     * @return RestTemplate
     */
    @Bean("payoutLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate payoutLoadBalancedRestTemplate() {
        return new RestTemplate();
    }
}
