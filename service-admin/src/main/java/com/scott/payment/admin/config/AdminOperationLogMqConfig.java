package com.scott.payment.admin.config;

import com.scott.payment.component.mq.enums.OperationLogSystemCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * service-admin 操作日志 MQ 标识配置
 */
@Configuration
public class AdminOperationLogMqConfig {

    /**
     * 声明当前服务的操作日志系统编码。
     *
     * @return 后台管理系统编码
     */
    @Bean
    public OperationLogSystemCode adminOperationLogSystemCode() {
        return OperationLogSystemCode.ADMIN;
    }
}
