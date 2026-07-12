package com.scott.payment.openapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentClientProperties
 * @date : 2026-05-31 21:13
 * @email : scott_x@163.com
 * @description : OpenAPI 调用 service-payment 的客户端配置
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentClientProperties
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIPayment Client 配置属性，位于 service-openapi 的配置层，用于承载该模块对应的业务职责和数据流转边界。
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
     * 内部服务调用方标识，用于 service-payment 审计调用来源。
     */
    private String internalCaller = "service-openapi";

    /**
     * 调用 service-payment 内部接口的 HMAC-SHA256 共享密钥。
     */
    private String internalSecret = "dev-internal-service-secret";
}
