package com.scott.payment.openapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentClientProperties
 * @date : 2026-07-14 12:30
 * @email : scott_x@163.com
 * @description : service-openapi 调用 service-payment 的客户端配置属性，统一维护各交易动作内部接口地址和内部服务签名密钥。
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
     * service-payment 内部授权接口地址。
     * <p>
     * 使用 lb:// 能力时通过 http://service-payment 交给 Spring Cloud LoadBalancer 解析服务实例。
     */
    private String authorizationUrl = "http://service-payment/internal/payment/authorization";

    /**
     * service-payment 内部一步支付接口地址。
     */
    private String paymentUrl = "http://service-payment/internal/payment/payment";

    /**
     * service-payment 内部预授权接口地址。
     */
    private String preAuthorizationUrl = "http://service-payment/internal/payment/pre-authorization";

    /**
     * service-payment 内部增量授权接口地址。
     */
    private String incrementalAuthorizationUrl = "http://service-payment/internal/payment/incremental-authorization";

    /**
     * service-payment 内部请款接口地址。
     */
    private String captureUrl = "http://service-payment/internal/payment/capture";

    /**
     * service-payment 内部预授权完成接口地址。
     */
    private String preAuthCompletionUrl = "http://service-payment/internal/payment/pre-auth-completion";

    /**
     * service-payment 内部退款接口地址。
     */
    private String refundUrl = "http://service-payment/internal/payment/refund";

    /**
     * service-payment 内部撤销接口地址。
     */
    private String voidUrl = "http://service-payment/internal/payment/void";

    /**
     * service-payment 内部交易查询接口地址。
     */
    private String queryUrl = "http://service-payment/internal/payment/query";

    /**
     * service-payment 内部渠道回调记录接口地址。
     */
    private String channelCallbackUrl = "http://service-payment/internal/payment/channel-callback";

    /**
     * service-payment 内部商户 API 响应日志回写接口地址。
     */
    private String merchantApiResponseLogUrl = "http://service-payment/internal/payment/transactions/merchant-api-logs/response";

    /**
     * 内部服务调用方标识，用于 service-payment 审计调用来源。
     */
    private String internalCaller = "service-openapi";

    /**
     * 调用 service-payment 内部接口的 HMAC-SHA256 共享密钥。
     */
    private String internalSecret = "dev-internal-service-secret";
}
