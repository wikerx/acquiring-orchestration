package com.scott.payment.openapi.config;

import com.scott.payment.component.web.trace.TraceIdRestTemplateCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutClientConfig
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : Payout Client Config 配置类，位于 商户开放接口服务，注册当前模块运行所需 Bean、拦截器、客户端或配置属性。
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
    public RestTemplate payoutRestTemplate(TraceIdRestTemplateCustomizer traceIdRestTemplateCustomizer,
                                           PayoutClientProperties properties) {
        properties.validate();
        return traceIdRestTemplateCustomizer.customize(new RestTemplate());
    }

    /**
     * 注册带负载均衡能力的 RestTemplate。
     *
     * @return RestTemplate
     */
    @Bean("payoutLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate payoutLoadBalancedRestTemplate(TraceIdRestTemplateCustomizer traceIdRestTemplateCustomizer) {
        return traceIdRestTemplateCustomizer.customize(new RestTemplate());
    }
}
