package com.scott.payment.merchant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalClientProperties
 * @date : 2026-07-19 00:00
 * @email : scott_x@163.com
 * @description : service-payment 内部客户端配置，位于 service-merchant 配置层，仅维护商户后台退款等状态变更动作调用支付核心的地址和内部签名密钥。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "merchant.payment-client")
public class PaymentInternalClientProperties {

    /**
     * 退款动作接口地址。
     */
    private String refundUrl = "http://service-payment/internal/payment/refund";

    /**
     * 内部服务调用方标识，用于 service-payment 审计调用来源。
     */
    private String internalCaller = "service-merchant";

    /**
     * 调用 service-payment 内部接口的 HMAC-SHA256 共享密钥。
     */
    private String internalSecret = "dev-internal-service-secret";
}
