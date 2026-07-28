package com.scott.payment.payment.config;

import com.scott.payment.component.web.trace.TraceIdRestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationConfig
 * @date : 2026-07-14 21:36
 * @email : scott_x@163.com
 * @description : 商户通知 HTTP 客户端配置，位于 service-payment 配置层，为交易结果通知提供独立 RestTemplate。
 * @status : create
 */
@Configuration
public class MerchantNotificationConfig {

    /**
     * 注册商户通知直连 RestTemplate。
     *
     * @return 商户通知 HTTP 客户端
     */
    @Bean("merchantNotificationRestTemplate")
    public RestTemplate merchantNotificationRestTemplate(TraceIdRestTemplateCustomizer traceIdRestTemplateCustomizer) {
        return traceIdRestTemplateCustomizer.customize(new RestTemplate());
    }
}
