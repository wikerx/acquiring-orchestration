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
 * @author : scott
 * @version : v1.0.0
 * @classname : DataInternalClientConfig
 * @date : 2026-08-01 16:00
 * @email : scott_x@163.com
 * @description : service-data 内部补偿客户端配置，为 service-job 提供直连和服务发现两类有界超时 HTTP 客户端
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(DataInternalClientProperties.class)
public class DataInternalClientConfig {

    /** service-data 内部调用建连超时，单位毫秒。 */
    private static final int CONNECT_TIMEOUT_MILLIS = 3_000;

    /** service-data 内部调用读取超时，单位毫秒。 */
    private static final int READ_TIMEOUT_MILLIS = 30_000;

    /**
     * 注册 service-data 直连客户端。
     *
     * @param traceCustomizer traceId 请求头定制器
     * @return 禁用系统代理的直连 RestTemplate
     */
    @Bean("jobDataInternalRestTemplate")
    public RestTemplate jobDataInternalRestTemplate(TraceIdRestTemplateCustomizer traceCustomizer) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setProxy(Proxy.NO_PROXY);
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);
        return traceCustomizer.customize(new RestTemplate(requestFactory));
    }

    /**
     * 注册支持 Nacos 服务发现的 service-data 客户端。
     *
     * @param restTemplateBuilder Spring RestTemplate 构造器
     * @param traceInterceptor traceId 请求拦截器
     * @return 负载均衡 RestTemplate
     */
    @Bean("jobDataInternalLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate jobDataInternalLoadBalancedRestTemplate(RestTemplateBuilder restTemplateBuilder,
                                                                TraceIdRestTemplateInterceptor traceInterceptor) {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MILLIS))
                .setReadTimeout(Duration.ofMillis(READ_TIMEOUT_MILLIS))
                .additionalInterceptors(traceInterceptor)
                .build();
    }
}
