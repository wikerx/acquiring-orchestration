package com.scott.payment.admin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileConfiguration
 * @date : 2026-09-14 19:10
 * @email : scott_x@163.com
 * @description : 注册商户开户资料安全配置，使 Nacos 中的敏感字段加密参数进入 Spring 配置体系
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(MerchantProfileProperties.class)
public class MerchantProfileConfiguration {
}
