package com.scott.payment.openapi.security;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantIpWhitelistDenyReason
 * @date : 2026-10-17 00:00
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI IP 准入内部拒绝原因，仅用于安全审计，不得直接返回给商户。
 * @status : create
 */
public enum MerchantIpWhitelistDenyReason {

    /** 商户没有审核通过且启用的精确 IP。 */
    WHITELIST_EMPTY,

    /** 可信接入 IP 缺失。 */
    CLIENT_IP_MISSING,

    /** 可信接入 IP 不是合法的精确 IPv4 或 IPv6 地址。 */
    CLIENT_IP_INVALID,

    /** 可信接入 IP 未命中商户白名单。 */
    IP_NOT_ALLOWED
}
