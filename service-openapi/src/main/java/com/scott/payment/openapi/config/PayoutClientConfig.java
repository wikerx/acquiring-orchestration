package com.scott.payment.openapi.config;

import com.scott.payment.component.web.trace.TraceIdRestTemplateCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;


@Configuration
@EnableConfigurationProperties(PayoutClientProperties.class)
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutClientConfig
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : PayoutClientConfig Spring 配置类，用于注册当前模块所需 Bean、客户端和拦截器，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class PayoutClientConfig {

    /**
     * 注册直连 RestTemplate。
     *
     * @return RestTemplate
     */
    @Bean("payoutRestTemplate")
    public RestTemplate payoutRestTemplate(TraceIdRestTemplateCustomizer traceIdRestTemplateCustomizer) {
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
