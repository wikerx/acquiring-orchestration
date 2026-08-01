package com.scott.payment.risk.domain;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 风控名单查询输入值。
 */
@Data
public class RiskRuntimeLookupValue {

    /**
     * 仅供需要明文范围计算的规范化原值；不得写入 Redis、审计日志或异常。
     */
    private String rawValue;

    /**
     * 精确名单匹配使用的不可逆 SHA-256 值。
     */
    private String matchValueHash;

    /**
     * 后台审计可展示的脱敏匹配值。
     */
    private String matchValueMasked;

    /**
     * IP 或 BIN 范围查询使用的十进制数值。
     */
    private BigDecimal numericValue;

    /**
     * IP 协议版本：IPV4 或 IPV6。
     */
    private String ipVersion;

    /**
     * ISO 3166-1 alpha-3 国家代码。
     */
    private String countryAlpha3;

    /**
     * 规范化省/州名称。
     */
    private String stateProvinceName;

    /**
     * 规范化城市名称。
     */
    private String cityName;

    /**
     * 来源 URL 规则匹配使用的规范化 host，不包含路径、查询参数和凭据。
     */
    private String sourceHost;
}
