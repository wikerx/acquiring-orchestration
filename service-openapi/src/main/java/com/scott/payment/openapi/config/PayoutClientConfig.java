package com.scott.payment.openapi.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * OpenAPI 代付内部服务调用配置。
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
