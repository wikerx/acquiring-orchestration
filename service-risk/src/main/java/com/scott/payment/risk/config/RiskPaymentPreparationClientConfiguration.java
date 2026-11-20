package com.scott.payment.risk.config;

import com.scott.payment.component.web.trace.TraceIdRestTemplateCustomizer;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/** 准备归属复核走内部服务发现，超时不作为释放证据。 */
@Configuration
public class RiskPaymentPreparationClientConfiguration {
    @Bean
    @LoadBalanced
    public RestTemplate riskPaymentPreparationRestTemplate(TraceIdRestTemplateCustomizer trace) {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(800);
        factory.setReadTimeout(1800);
        return trace.customize(new RestTemplate(factory));
    }
}
