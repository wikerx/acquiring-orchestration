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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminOperationLogMqConfig
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Admin Operation Log Mq 配置，位于 service-admin 的配置层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Configuration
public class AdminOperationLogMqConfig {

    /**
     * 声明当前服务的操作日志系统编码。
     *
     * @return 后台管理系统编码
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Bean
    public OperationLogSystemCode adminOperationLogSystemCode() {
        return OperationLogSystemCode.ADMIN;
    }
}
