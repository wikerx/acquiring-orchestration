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
 * @classname : PaymentClientConfig
 * @date : 2026-05-31 21:52
 * @email : scott_x@163.com
 * @description : Payment Client Config 配置类，位于 商户开放接口服务，注册当前模块运行所需 Bean、拦截器、客户端或配置属性。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties({PaymentClientProperties.class, HostedCheckoutProperties.class})
public class PaymentClientConfig {

    /**
     * 注册直连 RestTemplate。
     * <p>
     * 本地联调如果配置 `http://127.0.0.1:port/...` 或 `http://localhost:port/...`，
     * 必须使用直连客户端，避免 Spring Cloud LoadBalancer 把 IP 当作服务名解析。
     *
     * @return RestTemplate
     */
    @Bean("paymentRestTemplate")
    public RestTemplate paymentRestTemplate(TraceIdRestTemplateCustomizer traceIdRestTemplateCustomizer,
                                            PaymentClientProperties properties) {
        properties.validate();
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setProxy(Proxy.NO_PROXY);
        requestFactory.setConnectTimeout(properties.getConnectTimeoutMillis());
        requestFactory.setReadTimeout(properties.getReadTimeoutMillis());
        return traceIdRestTemplateCustomizer.customize(new RestTemplate(requestFactory));
    }

    /**
     * 注册带负载均衡能力的 RestTemplate。
     * <p>
     * dev、test、uat、prod 环境默认使用 `http://service-payment/...`，由 Nacos 服务发现和
     * Spring Cloud LoadBalancer 选择可用实例。
     *
     * @return RestTemplate
     */
    @Bean("paymentLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate paymentLoadBalancedRestTemplate(RestTemplateBuilder builder,
                                                        TraceIdRestTemplateInterceptor traceInterceptor,
                                                        PaymentClientProperties properties) {
        properties.validate();
        return builder
                .setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .additionalInterceptors(traceInterceptor)
                .build();
    }
}
