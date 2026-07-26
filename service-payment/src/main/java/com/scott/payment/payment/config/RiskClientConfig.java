package com.scott.payment.payment.config;

import com.scott.payment.component.web.trace.TraceIdRestTemplateCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskClientConfig
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : service-risk 内部调用配置，位于 service-payment 配置层，提供直连和服务发现两类 RestTemplate。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(RiskClientProperties.class)
public class RiskClientConfig {

    /**
     * 注册风控服务直连 RestTemplate。
     *
     * @return 直连 RestTemplate
     */
    @Bean("riskRestTemplate")
    public RestTemplate riskRestTemplate(TraceIdRestTemplateCustomizer traceIdRestTemplateCustomizer) {
        return traceIdRestTemplateCustomizer.customize(new RestTemplate());
    }

    /**
     * 注册风控服务负载均衡 RestTemplate。
     *
     * @return 负载均衡 RestTemplate
     */
    @Bean("riskLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate riskLoadBalancedRestTemplate(TraceIdRestTemplateCustomizer traceIdRestTemplateCustomizer) {
        return traceIdRestTemplateCustomizer.customize(new RestTemplate());
    }
}
