package com.scott.payment.merchant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantOperationLogProperties
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 商户管理系统操作日志上报配置
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "payment.operation-log")
public class MerchantOperationLogProperties {

    /**
     * 是否启用商户管理端操作日志上报。
     */
    private boolean enabled = true;

    /**
     * service-admin 操作日志写入地址。
     * <p>
     * 本地开发可配置为 http://127.0.0.1:8001/admin/system/oper-logs；
     * 注册中心调用可由后续网关或负载均衡 RestTemplate 统一替换。
     */
    private String adminRecordUrl = "http://127.0.0.1:8001/admin/system/oper-logs";
}
