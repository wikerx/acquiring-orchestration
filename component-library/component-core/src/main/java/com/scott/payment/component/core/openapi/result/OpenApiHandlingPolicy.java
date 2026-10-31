package com.scott.payment.component.core.openapi.result;

/**
 * OpenAPI V1.3 返回码对应的建议处理策略。
 *
 * <p>该策略只用于文档和受控流程提示，不能替代交易状态、幂等判断或授权客户端自动重试资金动作。</p>
 */
public enum OpenApiHandlingPolicy {
    NONE,
    QUERY,
    ITEMS,
    FIX,
    FIX_SECURITY,
    CONTACT,
    FIX_BUSINESS,
    WAIT,
    CONTEXT,
    CHANNEL_CONTEXT
}
