package com.scott.payment.merchant.config;

import com.scott.payment.component.mq.enums.OperationLogSystemCode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOperationLogMqConfig
 * @date : 2026-06-20 01:47
 * @email : scott_x@163.com
 * @description : service-merchant 操作日志 MQ 标识配置
 * @status : create
 */
@Configuration
public class MerchantOperationLogMqConfig {

    /**
     * 声明当前服务的操作日志系统编码。
     *
     * @return 商户管理系统编码
     */
    @Bean
    public OperationLogSystemCode merchantOperationLogSystemCode() {
        return OperationLogSystemCode.MERCHANT;
    }
}
