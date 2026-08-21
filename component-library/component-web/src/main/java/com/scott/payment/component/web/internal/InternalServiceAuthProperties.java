package com.scott.payment.component.web.internal;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : InternalServiceAuthProperties
 * @date : 2026-07-11 00:00
 * @email : scott_x@163.com
 * @description : 内部服务调用签名配置，约束 /internal/** 接口的共享密钥、时间窗和白名单边界。
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "internal-service.auth")
public class InternalServiceAuthProperties {

    /**
     * 是否启用内部服务签名校验；生产和预发环境必须保持开启。
     */
    private boolean enabled = true;

    /**
     * 调用方服务标识，用于审计内部调用来源。
     */
    private String caller = "service-openapi";

    /**
     * HMAC-SHA256 共享密钥；生产和预发环境必须通过环境变量或配置中心注入。
     */
    private String secret = "dev-internal-service-secret";

    /**
     * 请求时间允许偏移，超过该时间窗的请求会被拒绝。
     */
    private Duration allowedClockSkew = Duration.ofMinutes(5);

    /**
     * nonce 防重放记录有效期；必须覆盖正负时钟偏差窗口，默认十分钟。
     */
    private Duration nonceTtl = Duration.ofMinutes(10);

    /**
     * 不需要内部签名的路径，主要用于健康检查或基础探针。
     */
    private List<String> whitelist = List.of("/actuator/health/**", "/error");
}
