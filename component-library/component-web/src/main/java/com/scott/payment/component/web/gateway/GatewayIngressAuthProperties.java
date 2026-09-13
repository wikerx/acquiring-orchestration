package com.scott.payment.component.web.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayIngressAuthProperties
 * @date : 2026-08-08 00:00
 * @email : scott_x@163.com
 * @description : Servlet 下游的 Gateway 入口验签配置，为固定收银台路径提供外部共享密钥和时间窗。
 * @status : create
 */
@Component
@ConfigurationProperties(prefix = "acquiring.gateway-ingress")
public class GatewayIngressAuthProperties {

    /** 与 service-gateway 相同的环境专属 HMAC 密钥，不允许写入日志。 */
    private String secret;
    /** 下游接受 Gateway 签名的最大时间偏差，单位毫秒。 */
    private long allowedClockSkewMillis = 60_000L;
    /** 是否临时接受未绑定正文的旧入口签名，仅允许兼容环境显式开启。 */
    private boolean acceptLegacySignature = false;
    /** 是否强制使用 Redis 原子登记 nonce；UAT 和生产必须开启。 */
    private boolean replayProtectionRequired = true;
    /** 受保护外部请求正文最大字节数。 */
    private int maxRequestBodyBytes = 1024 * 1024;

    /** @return 外部注入的 HMAC 共享密钥 */
    public String getSecret() {
        return secret;
    }

    /** @param secret 外部注入的 HMAC 共享密钥 */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /** @return 允许的最大时钟偏差，单位毫秒 */
    public long getAllowedClockSkewMillis() {
        return allowedClockSkewMillis;
    }

    /** @param allowedClockSkewMillis 允许的最大时钟偏差，单位毫秒 */
    public void setAllowedClockSkewMillis(long allowedClockSkewMillis) {
        this.allowedClockSkewMillis = allowedClockSkewMillis;
    }

    public boolean isAcceptLegacySignature() {
        return acceptLegacySignature;
    }

    public void setAcceptLegacySignature(boolean acceptLegacySignature) {
        this.acceptLegacySignature = acceptLegacySignature;
    }

    public boolean isReplayProtectionRequired() {
        return replayProtectionRequired;
    }

    public void setReplayProtectionRequired(boolean replayProtectionRequired) {
        this.replayProtectionRequired = replayProtectionRequired;
    }

    public int getMaxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    public void setMaxRequestBodyBytes(int maxRequestBodyBytes) {
        this.maxRequestBodyBytes = maxRequestBodyBytes;
    }

}
