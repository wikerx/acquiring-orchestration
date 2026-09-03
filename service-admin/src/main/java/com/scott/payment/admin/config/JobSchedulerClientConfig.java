package com.scott.payment.admin.config;

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

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobSchedulerClientConfig
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务调度客户端配置类
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(JobSchedulerClientProperties.class)
public class JobSchedulerClientConfig {

    /**
     * 任务调度内部直连超时时间。
     */
    private static final int CONNECT_TIMEOUT_MILLIS = 3_000;

    /**
     * 任务调度内部直连读取超时时间。
     */
    private static final int READ_TIMEOUT_MILLIS = 10_000;

    /**
     * 注册直连 RestTemplate。
     *
     * @return 直连 RestTemplate
     */
    @Bean("jobSchedulerRestTemplate")
    public RestTemplate jobSchedulerRestTemplate(TraceIdRestTemplateCustomizer traceIdRestTemplateCustomizer,
                                                 JobSchedulerClientProperties properties) {
        properties.validate();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setProxy(Proxy.NO_PROXY);
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);
        return traceIdRestTemplateCustomizer.customize(new RestTemplate(requestFactory));
    }

    /**
     * 注册负载均衡 RestTemplate。
     *
     * @return 负载均衡 RestTemplate
     */
    @Bean("jobSchedulerLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate jobSchedulerLoadBalancedRestTemplate(RestTemplateBuilder restTemplateBuilder,
                                                             TraceIdRestTemplateInterceptor traceIdRestTemplateInterceptor) {
        return restTemplateBuilder
                .setConnectTimeout(java.time.Duration.ofMillis(CONNECT_TIMEOUT_MILLIS))
                .setReadTimeout(java.time.Duration.ofMillis(READ_TIMEOUT_MILLIS))
                .additionalInterceptors(traceIdRestTemplateInterceptor)
                .build();
    }
}
