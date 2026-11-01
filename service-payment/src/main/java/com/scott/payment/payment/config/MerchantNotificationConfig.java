package com.scott.payment.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册商户通知配置并在 service-payment 启动时执行重试次数边界校验。
 */
@Configuration
@EnableConfigurationProperties(MerchantNotificationProperties.class)
public class MerchantNotificationConfig {
}
