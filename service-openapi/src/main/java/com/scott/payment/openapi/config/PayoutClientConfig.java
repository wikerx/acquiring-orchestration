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
 * @description : 商户 OpenAPIPayout Client 配置，位于 service-openapi 的配置层，用于承载该模块对应的业务职责和数据流转边界。
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
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean("payoutLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate payoutLoadBalancedRestTemplate() {
        return new RestTemplate();
    }
}
