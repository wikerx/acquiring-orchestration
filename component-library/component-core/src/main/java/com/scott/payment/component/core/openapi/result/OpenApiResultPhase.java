package com.scott.payment.component.core.openapi.result;

/**
 * OpenAPI V1.3 结果产生的处理阶段。
 *
 * <p>阶段仅用于内部登记、审计和诊断，不作为对外响应字段，也不替代支付核心交易状态。</p>
 */
public enum OpenApiResultPhase {
    /**
     * REQUEST 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    REQUEST,
    /**
     * SECURITY 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    SECURITY,
    /**
     * AUTHORIZATION 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    AUTHORIZATION,
    /**
     * CONFIGURATION 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CONFIGURATION,
    /**
     * BUSINESS 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    BUSINESS,
    /**
     * RISK 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    RISK,
    /**
     * CHANNEL 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CHANNEL,
    /**
     * PLATFORM 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    PLATFORM,
    RESPONSE
}
