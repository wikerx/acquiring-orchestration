package com.scott.payment.merchant.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileConfiguration
 * @date : 2026-09-20 12:00
 * @email : scott_x@163.com
 * @description : 商户资料安全配置注册入口
 * @status : create
 */
@Configuration
@EnableConfigurationProperties(MerchantProfileProperties.class)
public class MerchantProfileConfiguration {
}
