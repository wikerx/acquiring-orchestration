package com.scott.payment.component.core.openapi.result;

/**
 * OpenAPI V1.3 结果产生的处理阶段。
 *
 * <p>阶段仅用于内部登记、审计和诊断，不作为对外响应字段，也不替代支付核心交易状态。</p>
 */
public enum OpenApiResultPhase {
    REQUEST,
    SECURITY,
    AUTHORIZATION,
    CONFIGURATION,
    BUSINESS,
    RISK,
    CHANNEL,
    PLATFORM,
    RESPONSE
}
