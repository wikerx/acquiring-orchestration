package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentFailureReasonEnum
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 收单交易失败原因枚举，位于 service-payment 领域状态层，用于在 transaction_status=FAILED 时区分风控、路由、渠道和状态机等失败来源。
 * @status : create
 */
@Getter
public enum PaymentFailureReasonEnum {

    /**
     * 风控拒绝交易。
     */
    RISK_REJECTED("RISK_REJECTED"),

    /**
     * 渠道路由失败。
     */
    ROUTE_FAILED("ROUTE_FAILED"),

    /**
     * 渠道不支持当前交易能力。
     */
    CHANNEL_UNSUPPORTED("CHANNEL_UNSUPPORTED"),

    /**
     * 渠道不支持标签币种且系统交易汇率不存在。
     */
    EXCHANGE_RATE_NOT_FOUND("EXCHANGE_RATE_NOT_FOUND"),

    /**
     * 渠道请求失败。
     */
    CHANNEL_REQUEST_FAILED("CHANNEL_REQUEST_FAILED"),

    /**
     * 渠道响应解析失败。
     */
    CHANNEL_RESPONSE_INVALID("CHANNEL_RESPONSE_INVALID"),

    /**
     * 渠道请求超时。
     */
    CHANNEL_TIMEOUT("CHANNEL_TIMEOUT"),

    /**
     * 交易状态流转不允许。
     */
    STATE_TRANSITION_DENIED("STATE_TRANSITION_DENIED");

    private final String code;

    /**
     * 创建失败原因。
     *
     * @param code 失败原因编码
     */
    PaymentFailureReasonEnum(String code) {
        this.code = code;
    }
}
