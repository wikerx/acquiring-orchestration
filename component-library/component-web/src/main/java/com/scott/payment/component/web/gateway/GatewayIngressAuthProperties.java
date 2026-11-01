package com.scott.payment.component.web.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Servlet 下游的 Gateway 入口验签配置，为固定收银台路径提供外部共享密钥和时间窗。
 */
@Component
@ConfigurationProperties(prefix = "acquiring.gateway-ingress")
public class GatewayIngressAuthProperties {

    /**
     * 与 service-gateway 相同的环境专属 HMAC 密钥，不允许写入日志。
     */
    private String secret;
    /**
     * 下游接受 Gateway 签名的最大时间偏差，单位毫秒。
     */
    private long allowedClockSkewMillis = 60_000L;
    /**
     * 是否临时接受未绑定正文的旧入口签名，仅允许兼容环境显式开启。
     */
    private boolean acceptLegacySignature = false;
    /**
     * 是否强制使用 Redis 原子登记 nonce；UAT 和生产必须开启。
     */
    private boolean replayProtectionRequired = true;
    /**
     * 受保护外部请求正文最大字节数。
     */
    private int maxRequestBodyBytes = 1024 * 1024;
    /**
     * Admin/Merchant 门户是否强制经过 Gateway HMAC；默认开启，dev/test 可显式关闭直连联调。
     */
    private boolean protectPortalPaths = true;

    /**
     * 获取外部注入的 Gateway HMAC 共享密钥。
     *
     * @return HMAC 共享密钥
     */
    public String getSecret() {
        return secret;
    }

    /**
     * 设置外部注入的 Gateway HMAC 共享密钥。
     *
     * @param secret HMAC 共享密钥
     */
    public void setSecret(String secret) {
        this.secret = secret;
    }

    /**
     * 获取允许的最大签名时钟偏差。
     *
     * @return 最大时钟偏差，单位毫秒
     */
    public long getAllowedClockSkewMillis() {
        return allowedClockSkewMillis;
    }

    /**
     * 设置允许的最大签名时钟偏差。
     *
     * @param allowedClockSkewMillis 最大时钟偏差，单位毫秒
     */
    public void setAllowedClockSkewMillis(long allowedClockSkewMillis) {
        this.allowedClockSkewMillis = allowedClockSkewMillis;
    }


    /**
     * 判断是否在迁移窗口内接受未绑定正文的旧版入口签名。
     *
     * @return 允许旧版签名时返回 true
     */
    public boolean isAcceptLegacySignature() {
        return acceptLegacySignature;
    }

    /**
     * 配置是否临时兼容旧版入口签名格式。
     *
     * @param acceptLegacySignature 允许旧版签名时为 {@code true}
     */
    public void setAcceptLegacySignature(boolean acceptLegacySignature) {
        this.acceptLegacySignature = acceptLegacySignature;
    }


    /**
     * 判断是否强制通过 Redis 原子登记 nonce 防止请求重放。
     *
     * @return 必须启用防重放时返回 true
     */
    public boolean isReplayProtectionRequired() {
        return replayProtectionRequired;
    }

    /**
     * 写入防重放保护必需标识，保持配置属性或测试夹具中的字段值与调用方输入一致。
     * @param replayProtectionRequired 所有受保护入口必须完成防重放校验时为 {@code true}
     */
    public void setReplayProtectionRequired(boolean replayProtectionRequired) {
        this.replayProtectionRequired = replayProtectionRequired;
    }

    /**
     * 查询最大请求报文体字节数；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 公共组件库 既有权限、数据范围和空结果约定。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public int getMaxRequestBodyBytes() {
        return maxRequestBodyBytes;
    }

    /**
     * 写入最大请求报文体字节数，保持配置属性或测试夹具中的字段值与调用方输入一致。
     * @param maxRequestBodyBytes 允许缓存和验签的最大请求报文体字节数
     */
    public void setMaxRequestBodyBytes(int maxRequestBodyBytes) {
        this.maxRequestBodyBytes = maxRequestBodyBytes;
    }


    /**
     * 判断管理后台和商户后台公网路径是否必须经过 Gateway HMAC。
     *
     * @return 门户路径受保护时返回 true
     */
    public boolean isProtectPortalPaths() {
        return protectPortalPaths;
    }

    /**
     * 配置是否对管理端和商户端入口路径启用统一验签保护。
     *
     * @param protectPortalPaths 需要保护 Portal 路径时为 {@code true}
     */
    public void setProtectPortalPaths(boolean protectPortalPaths) {
        this.protectPortalPaths = protectPortalPaths;
    }

}
