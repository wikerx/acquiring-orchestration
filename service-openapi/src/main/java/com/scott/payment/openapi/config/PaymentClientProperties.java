package com.scott.payment.openapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentClientProperties
 * @date : 2026-07-14 12:30
 * @email : scott_x@163.com
 * @description : service-openapi 调用 service-payment 的客户端配置属性，仅维护远程调用开关和内部服务签名密钥。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "openapi.payment-client")
public class PaymentClientProperties {

    /**
     * 是否启用远程 service-payment 调用。
     * <p>
     * dev 联调和生产应保持 true；单元测试可设为 false，让 OpenAPI 保留本地模拟响应。
     */
    private boolean remoteEnabled = true;

    /**
     * 内部服务调用方标识，用于 service-payment 审计调用来源。
     */
    private String internalCaller = "service-openapi";

    /**
     * 调用 service-payment 内部接口的 HMAC-SHA256 共享密钥。
     */
    private String internalSecret = "dev-internal-service-secret";
}
