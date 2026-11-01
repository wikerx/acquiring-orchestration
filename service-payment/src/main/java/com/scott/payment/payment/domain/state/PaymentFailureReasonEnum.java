package com.scott.payment.payment.domain.state;

import lombok.Getter;

/**
 * 支付失败原因枚举，位于 支付核心服务，集中定义该状态或类型的受控取值，禁止业务代码使用未声明字符串替代。
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
     * 渠道明确返回失败或拒绝，具备可审计的渠道响应证据。
     */
    CHANNEL_DECLINED("CHANNEL_DECLINED"),

    /**
     * 本地配置或序列化等发送前故障，已确认请求没有到达渠道。
     */
    CHANNEL_REQUEST_NOT_SENT("CHANNEL_REQUEST_NOT_SENT"),

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
