package com.scott.payment.admin.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 启用统一运营待办配置绑定，使各环境可以覆盖查询预算和来源 SLA。
 */
@Configuration
@EnableConfigurationProperties(AdminOperationalWorkItemProperties.class)
public class AdminOperationalWorkItemConfiguration {
}
