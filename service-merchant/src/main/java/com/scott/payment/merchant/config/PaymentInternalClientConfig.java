package com.scott.payment.merchant.config;

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
 * @classname : PaymentInternalClientConfig
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : service-payment 内部客户端配置，位于 service-merchant 配置层，仅用于退款等状态变更内部调用。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(PaymentInternalClientProperties.class)
public class PaymentInternalClientConfig {

    /**
     * service-payment 内部状态变更调用建连超时时间。
     */
    private static final int CONNECT_TIMEOUT_MILLIS = 3_000;

    /**
     * service-payment 内部状态变更调用读取超时时间。
     */
    private static final int READ_TIMEOUT_MILLIS = 10_000;

    /**
     * 注册 service-payment 直连 RestTemplate。
     *
     * @return 直连 RestTemplate
     */
    @Bean("merchantPaymentInternalRestTemplate")
    public RestTemplate merchantPaymentInternalRestTemplate(TraceIdRestTemplateCustomizer traceIdRestTemplateCustomizer) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setProxy(Proxy.NO_PROXY);
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        requestFactory.setReadTimeout(READ_TIMEOUT_MILLIS);
        return traceIdRestTemplateCustomizer.customize(new RestTemplate(requestFactory));
    }

    /**
     * 注册 service-payment 负载均衡 RestTemplate。
     *
     * @param restTemplateBuilder RestTemplate 构造器
     * @return 负载均衡 RestTemplate
     */
    @Bean("merchantPaymentInternalLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate merchantPaymentInternalLoadBalancedRestTemplate(RestTemplateBuilder restTemplateBuilder,
                                                                        TraceIdRestTemplateInterceptor traceIdRestTemplateInterceptor) {
        return restTemplateBuilder
                .setConnectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MILLIS))
                .setReadTimeout(Duration.ofMillis(READ_TIMEOUT_MILLIS))
                .additionalInterceptors(traceIdRestTemplateInterceptor)
                .build();
    }
}
