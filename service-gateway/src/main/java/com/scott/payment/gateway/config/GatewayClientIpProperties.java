package com.scott.payment.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GatewayClientIpProperties
 * @date : 2026-07-18 00:00
 * @email : scott_x@163.com
 * @description : 网关客户端 IP 透传配置，位于 service-gateway 配置层，用于控制是否信任上游代理写入的转发头。
 * @status : create
 */
@ConfigurationProperties(prefix = "acquiring.gateway.client-ip")
public class GatewayClientIpProperties {

    /**
     * 是否允许解析 {@code X-Forwarded-For}，默认 {@code false}。
     * 开启后仍要求当前 TCP 直连地址命中 {@link #trustedProxyCidrs}，否则继续使用 TCP 地址，
     * 防止公网调用方伪造转发头绕过 IP 白名单和限流。
     */
    private boolean trustForwardedHeader = false;

    /**
     * 允许提供转发链的 TCP 直接上游 CIDR，支持 IPv4 和 IPv6。
     * 配置文件可写成列表，环境变量使用英文逗号分隔；为空时不信任任何转发头。
     */
    private List<String> trustedProxyCidrs = new ArrayList<>();

    /**
     * 获取是否信任转发头。
     *
     * @return {@code true} 表示在直接上游可信时解析 {@code X-Forwarded-For}
     */
    public boolean isTrustForwardedHeader() {
        return trustForwardedHeader;
    }

    /**
     * 设置是否信任转发头。
     *
     * @param trustForwardedHeader {@code true} 表示启用可信代理链解析
     */
    public void setTrustForwardedHeader(boolean trustForwardedHeader) {
        this.trustForwardedHeader = trustForwardedHeader;
    }

    /**
     * 获取允许提供转发链的可信代理网段。
     *
     * @return IPv4/IPv6 CIDR 列表；空列表表示不信任任何代理
     */
    public List<String> getTrustedProxyCidrs() {
        return trustedProxyCidrs;
    }

    /**
     * 设置可信代理网段，并过滤绑定过程中产生的空白元素。
     * CIDR 格式和前缀范围由 {@code TrustedProxyMatcher} 在网关启动时校验。
     *
     * @param trustedProxyCidrs IPv4/IPv6 CIDR 列表
     */
    public void setTrustedProxyCidrs(List<String> trustedProxyCidrs) {
        this.trustedProxyCidrs = trustedProxyCidrs == null
                ? new ArrayList<>()
                : trustedProxyCidrs.stream().filter(StringUtils::hasText).toList();
    }
}
