package com.scott.payment.job.config;

import com.scott.payment.component.web.internal.InternalServiceClientCredentialValidator;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalClientProperties
 * @date : 2026-07-15 00:00
 * @email : scott_x@163.com
 * @description : service-job 调用 service-payment 内部补偿接口的客户端配置，位于 service-job 配置层，仅维护内部签名密钥。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "job.payment-client")
public class PaymentInternalClientProperties {

    /**
     * 内部服务调用方标识，用于 service-payment 审计调用来源。
     */
    private String internalCaller = "service-job";

    /**
     * 调用 service-payment 内部接口的 HMAC-SHA256 共享密钥。
     */
    private String internalSecret;

    /** 启动前校验固定调用方和 Nacos 注入的 active 密钥。 */
    public void validate() {
        InternalServiceClientCredentialValidator.validate(
                "job payment-client", "service-job", internalCaller, internalSecret);
    }
}
