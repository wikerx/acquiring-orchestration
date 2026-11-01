package com.scott.payment.component.core.openapi.result;

/**
 * OpenAPI V1.3 返回码对应的建议处理策略。
 *
 * <p>该策略只用于文档和受控流程提示，不能替代交易状态、幂等判断或授权客户端自动重试资金动作。</p>
 */
public enum OpenApiHandlingPolicy {
    /**
     * NONE 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    NONE,
    /**
     * QUERY 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    QUERY,
    /**
     * ITEMS 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    ITEMS,
    /**
     * FIX 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    FIX,
    /**
     * FIX SECURITY 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    FIX_SECURITY,
    /**
     * CONTACT 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CONTACT,
    /**
     * FIX BUSINESS 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    FIX_BUSINESS,
    /**
     * WAIT 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    WAIT,
    /**
     * CONTEXT 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CONTEXT,
    CHANNEL_CONTEXT
}
