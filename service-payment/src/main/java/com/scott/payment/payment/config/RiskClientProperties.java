package com.scott.payment.payment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskClientProperties
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : service-payment 调用 service-risk 的客户端配置，控制风控远程调用开关和内部服务签名密钥。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "payment.risk-client")
public class RiskClientProperties {

    /**
     * 是否启用远程 service-risk 调用；本地骨架默认关闭，生产接入前必须通过配置中心开启并关闭 Noop 风控。
     */
    private boolean remoteEnabled = false;

    /**
     * 内部服务调用方标识。
     */
    private String internalCaller = "service-payment";

    /**
     * 调用 service-risk 内部接口的 HMAC-SHA256 共享密钥。
     */
    private String internalSecret = "dev-internal-service-secret";
}
