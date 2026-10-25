package com.scott.payment.openapi.config;

import com.scott.payment.component.web.trace.TraceIdRestTemplateCustomizer;
import com.scott.payment.component.web.trace.TraceIdRestTemplateInterceptor;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.Proxy;
import java.time.Duration;

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
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setProxy(Proxy.NO_PROXY);
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMillis());
        requestFactory.setReadTimeout(properties.getReadTimeoutMillis());
        return traceIdRestTemplateCustomizer.customize(new RestTemplate(requestFactory));
    }

    /**
     * 注册带负载均衡能力的 RestTemplate。
     *
     * @return RestTemplate
     */
    @Bean("payoutLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate payoutLoadBalancedRestTemplate(RestTemplateBuilder builder,
                                                       TraceIdRestTemplateInterceptor traceInterceptor,
                                                       PayoutClientProperties properties) {
        properties.validate();
        return builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .additionalInterceptors(traceInterceptor)
                .build();
    }
}
