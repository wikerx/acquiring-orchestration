package com.scott.payment.openapi.security;

/**
 * 商户 OpenAPI IP 准入内部拒绝原因，仅用于安全审计，不得直接返回给商户。
 */
public enum MerchantIpWhitelistDenyReason {

    /**
     * 商户没有审核通过且启用的精确 IP。
     */
    WHITELIST_EMPTY,

    /**
     * 可信接入 IP 缺失。
     */
    CLIENT_IP_MISSING,

    /**
     * 可信接入 IP 不是合法的精确 IPv4 或 IPv6 地址。
     */
    CLIENT_IP_INVALID,

    /**
     * 可信接入 IP 未命中商户白名单。
     */
    IP_NOT_ALLOWED
}
