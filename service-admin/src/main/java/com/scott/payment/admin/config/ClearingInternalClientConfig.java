package com.scott.payment.admin.config;

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

/** 清分管理内部客户端的直连和服务发现 HTTP 配置。 */
@Configuration
@EnableConfigurationProperties(ClearingInternalClientProperties.class)
public class ClearingInternalClientConfig {

    @Bean("adminClearingInternalRestTemplate")
    public RestTemplate direct(TraceIdRestTemplateCustomizer customizer,
                               ClearingInternalClientProperties properties) {
        validate(properties);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setProxy(Proxy.NO_PROXY);
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        return customizer.customize(new RestTemplate(factory));
    }

    @Bean("adminClearingInternalLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate loadBalanced(RestTemplateBuilder builder,
                                     TraceIdRestTemplateInterceptor interceptor,
                                     ClearingInternalClientProperties properties) {
        validate(properties);
        return builder.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .additionalInterceptors(interceptor).build();
    }

    private void validate(ClearingInternalClientProperties properties) {
        properties.validate();
    }
}
