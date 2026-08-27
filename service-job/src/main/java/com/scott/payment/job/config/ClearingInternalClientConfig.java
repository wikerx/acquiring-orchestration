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

/** 清分内部客户端直连和服务发现 RestTemplate 配置。 */
@Configuration
@EnableConfigurationProperties(ClearingInternalClientProperties.class)
public class ClearingInternalClientConfig {

    @Bean("jobClearingInternalRestTemplate")
    public RestTemplate direct(TraceIdRestTemplateCustomizer traceCustomizer,
                               ClearingInternalClientProperties properties) {
        validate(properties);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setProxy(Proxy.NO_PROXY);
        factory.setConnectTimeout(properties.getConnectTimeoutMillis());
        factory.setReadTimeout(properties.getReadTimeoutMillis());
        return traceCustomizer.customize(new RestTemplate(factory));
    }

    @Bean("jobClearingInternalLoadBalancedRestTemplate")
    @LoadBalanced
    public RestTemplate loadBalanced(RestTemplateBuilder builder,
                                     TraceIdRestTemplateInterceptor traceInterceptor,
                                     ClearingInternalClientProperties properties) {
        validate(properties);
        return builder.setConnectTimeout(Duration.ofMillis(properties.getConnectTimeoutMillis()))
                .setReadTimeout(Duration.ofMillis(properties.getReadTimeoutMillis()))
                .additionalInterceptors(traceInterceptor).build();
    }

    private void validate(ClearingInternalClientProperties properties) {
        properties.validate();
    }
}
