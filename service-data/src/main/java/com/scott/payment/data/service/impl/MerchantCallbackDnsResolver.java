package com.scott.payment.data.service.impl;

import org.apache.hc.client5.http.DnsResolver;

import java.net.InetAddress;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantCallbackDnsResolver
 * @date : 2026-10-25 00:00
 * @email : scott_x@163.com
 * @description : 商户回调 HTTP 客户端专用 DNS Resolver，在套接字建连使用的同一次解析上执行环境网络策略，阻断 DNS 重绑定绕过。
 * @status : create
 */
public class MerchantCallbackDnsResolver implements DnsResolver {

    private final MerchantCallbackTargetValidator targetValidator;

    /**
     * 创建绑定回调目标策略的连接时解析器。
     *
     * @param targetValidator 商户回调目标校验器
     */
    public MerchantCallbackDnsResolver(MerchantCallbackTargetValidator targetValidator) {
        this.targetValidator = targetValidator;
    }

    /**
     * 返回已经过环境网络策略校验、并将直接用于连接的地址集合。
     *
     * @param host HTTP 目标主机名
     * @return 当前允许连接的全部地址
     */
    @Override
    public InetAddress[] resolve(String host) {
        return targetValidator.resolveAllowedAddresses(host);
    }

    /**
     * 保留原始主机名用于 TLS SNI 和主机名校验，不执行第二次地址解析。
     *
     * @param host HTTP 目标主机名
     * @return 未改写的主机名
     */
    @Override
    public String resolveCanonicalHostname(String host) {
        return host;
    }
}
