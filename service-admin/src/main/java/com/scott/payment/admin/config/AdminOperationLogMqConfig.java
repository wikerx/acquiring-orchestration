package com.scott.payment.admin.config;

import com.scott.payment.component.mq.enums.OperationLogSystemCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOperationLogMqConfig
 * @date : 2026-06-20 01:47
 * @email : scott_x@163.com
 * @description : service-admin 操作日志 MQ 标识配置
 * @status : create
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
