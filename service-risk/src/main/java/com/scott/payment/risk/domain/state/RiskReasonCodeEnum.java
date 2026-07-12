package com.scott.payment.risk.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskReasonCodeEnum
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 风控原因码枚举，位于 service-risk 领域状态层，用于解释风控决策来源并支撑后续人工复核和审计。
 * @status : create
 */
@Getter
public enum RiskReasonCodeEnum {

    /**
     * 未命中风险规则。
     */
    NONE("NONE", "risk rule not hit"),

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
