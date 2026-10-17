package com.scott.payment.payment.config;

import com.scott.payment.component.web.internal.InternalServiceClientCredentialValidator;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskClientProperties
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : service-payment 调用 service-risk 的客户端配置，维护固定调用方和内部服务签名密钥。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "payment.risk-client")
public class RiskClientProperties {

    /**
     * 内部服务调用方标识。
     */
    private String internalCaller = "service-payment";

    /**
     * 调用 service-risk 内部接口的 HMAC-SHA256 共享密钥。
     */
    private String internalSecret;

    /** 校验固定调用方和 Nacos 注入的 active 密钥，禁止无密钥启动支付交易链路。 */
    public void validate() {
        InternalServiceClientCredentialValidator.validate(
                "payment risk-client", "service-payment", internalCaller, internalSecret);
    }
}
