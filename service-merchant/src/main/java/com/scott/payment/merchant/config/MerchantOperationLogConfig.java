package com.scott.payment.merchant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOperationLogConfig
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 商户管理系统操作日志配置入口
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(MerchantOperationLogProperties.class)
public class MerchantOperationLogConfig {
}
