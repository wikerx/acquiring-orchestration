package com.scott.payment.openapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


@Data
@ConfigurationProperties(prefix = "openapi.payout-client")
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PayoutClientProperties
 * @date : 2026-06-19 19:19
 * @email : scott_x@163.com
 * @description : Payout Client Properties 配置属性模型，位于 商户开放接口服务，绑定 application 配置项并提供运行时默认值。
 * @status : create
 */
public class PayoutClientProperties {

    /**
     * 是否启用远程 service-payout 调用。
     */
    private boolean remoteEnabled = true;

    /**
     * service-payout 内部创建接口地址。
     */
    private String createUrl = "http://service-payout/internal/payout/create";

    /**
     * 内部服务调用方标识，用于 service-payout 审计调用来源。
     */
    private String internalCaller = "service-openapi";

    /**
     * 调用 service-payout 内部接口的 HMAC-SHA256 共享密钥。
     */
    private String internalSecret = "dev-internal-service-secret";
}
