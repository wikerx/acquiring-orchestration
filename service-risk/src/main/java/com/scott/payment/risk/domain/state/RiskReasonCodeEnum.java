package com.scott.payment.risk.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskReasonCodeEnum
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 风控原因编码枚举，位于 风控服务，集中定义该状态或类型的受控取值，禁止业务代码使用未声明字符串替代。
 * @status : create
 */
@Getter
public enum RiskReasonCodeEnum {

    /**
     * 未命中风险规则。
     */
    NONE("NONE", "risk rule not hit"),

    /**
     * 当前交易没有可适用的已配置风控规则。
     */
    NO_APPLICABLE_RULE("NO_APPLICABLE_RULE", "no applicable risk rule"),

    /**
     * 已配置的名单规则未匹配当前交易。
     */
    NO_MATCH("NO_MATCH", "configured risk rule did not match"),

    /**
     * 商户未开通当前可选风控功能。
     */
    NOT_ENABLED("NOT_ENABLED", "merchant risk function is not enabled"),

    /**
     * 商户已开通功能但没有配置可执行规则。
     */
    NOT_CONFIGURED("NOT_CONFIGURED", "merchant risk function is not configured"),

    /**
     * 已配置规则所需的交易输入缺失。
     */
    INPUT_MISSING("INPUT_MISSING", "risk rule input is missing"),

    /**
     * 请求参数缺失或非法。
     */
    PARAM_INVALID("PARAM_INVALID", "risk request parameter is invalid"),

    /**
     * 请求来源网址命中阻断名单。
     */
    BLOCKED_SOURCE("BLOCKED_SOURCE", "request source is blocked"),

    /**
     * 付款人 IP 命中阻断名单。
     */
    BLOCKED_IP("BLOCKED_IP", "payer ip is blocked"),

    /**
     * 商户启用 IP 白名单但当前请求 IP 未命中。
     */
    IP_WHITELIST_MISSED("IP_WHITELIST_MISSED", "merchant ip whitelist is missed"),

    /**
     * 商户启用来源网址限定但当前来源未命中。
     */
    SOURCE_URL_NOT_ALLOWED("SOURCE_URL_NOT_ALLOWED", "merchant source url is not allowed"),

    /**
     * 命中黑名单。
     */
    BLACKLIST_HIT("BLACKLIST_HIT", "blacklist rule is hit"),

    /**
     * 命中 AML 名单。
     */
    AML_HIT("AML_HIT", "aml rule is hit"),

    /**
     * 命中内风控规则。
     */
    RULE_HIT("RULE_HIT", "risk rule is hit"),

    /**
     * 命中交易频率限定。
     */
    FREQUENCY_LIMIT_HIT("FREQUENCY_LIMIT_HIT", "transaction frequency limit is hit"),

    /**
     * 交易金额达到 3DS 认证要求。
     */
    THREE_DS_REQUIRED("THREE_DS_REQUIRED", "3ds authentication is required"),

    /**
     * 交易达到人工复核阈值。
     */
    MANUAL_REVIEW_REQUIRED("MANUAL_REVIEW_REQUIRED", "manual review is required");

    /**
     * 风控原因码。
     */
    private final String code;

    /**
     * 内部原因描述。
     */
    private final String message;

    RiskReasonCodeEnum(String code, String message) {
        this.code = code;
        this.message = message;
    }
}
