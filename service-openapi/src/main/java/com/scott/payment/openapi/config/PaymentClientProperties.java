package com.scott.payment.openapi.config;

import com.scott.payment.component.web.internal.InternalServiceClientCredentialValidator;
import com.scott.payment.component.web.internal.InternalHttpTimeoutPolicy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentClientProperties
 * @date : 2026-07-14 12:30
 * @email : scott_x@163.com
 * @description : service-openapi 调用 service-payment 的客户端配置属性，维护固定调用方和内部服务签名密钥。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "openapi.payment-client")
public class PaymentClientProperties {

    /** 建立 service-payment 连接的超时，单位毫秒。 */
    private int connectTimeoutMillis = InternalHttpTimeoutPolicy.DEFAULT_CONNECT_TIMEOUT_MILLIS;

    /** 等待 service-payment 同步响应的超时，单位毫秒。 */
    private int readTimeoutMillis = InternalHttpTimeoutPolicy.DEFAULT_READ_TIMEOUT_MILLIS;

    /**
     * 内部服务调用方标识，用于 service-payment 审计调用来源。
     */
    private String internalCaller = "service-openapi";

    /**
     * 调用 service-payment 内部接口的 HMAC-SHA256 共享密钥。
     */
    private String internalSecret;

    /** 校验固定调用方和 Nacos 注入的 active 密钥，禁止无密钥启动交易入口。 */
    public void validate() {
        InternalServiceClientCredentialValidator.validate(
                "openapi payment-client", "service-openapi", internalCaller, internalSecret);
        InternalHttpTimeoutPolicy.validate(
                "openapi payment-client", connectTimeoutMillis, readTimeoutMillis);
    }
}
