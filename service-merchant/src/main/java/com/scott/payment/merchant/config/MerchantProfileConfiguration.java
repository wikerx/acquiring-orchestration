package com.scott.payment.merchant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 商户资料安全配置注册入口。
 */
@Configuration
@EnableConfigurationProperties(MerchantProfileProperties.class)
public class MerchantProfileConfiguration {
}
