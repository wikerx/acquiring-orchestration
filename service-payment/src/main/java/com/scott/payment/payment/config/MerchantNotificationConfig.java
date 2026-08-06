package com.scott.payment.payment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantNotificationConfig
 * @date : 2026-08-03 00:00
 * @email : scott_x@163.com
 * @description : 注册商户通知配置并在 service-payment 启动时执行重试次数边界校验。
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(MerchantNotificationProperties.class)
public class MerchantNotificationConfig {
}
