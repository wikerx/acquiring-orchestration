package com.scott.payment.admin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册商户开户资料安全配置，使 Nacos 中的敏感字段加密参数进入 Spring 配置体系
 */
@Configuration
@EnableConfigurationProperties(MerchantProfileProperties.class)
public class MerchantProfileConfiguration {
}
