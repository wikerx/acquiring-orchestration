package com.scott.payment.openapi.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * OpenAPI 基础数据只读快照的定时刷新配置
 */
@Configuration
@EnableScheduling
public class OpenApiReferenceDataRefreshConfig {
}
