package com.scott.payment.component.core.openapi.result;

/**
 * OpenAPI 返回文案的可信展示对象。
 *
 * <p>展示对象必须由服务端入口和认证上下文确定，禁止直接采信客户端提交的角色或 audience 参数。</p>
 */
public enum OpenApiMessageAudience {
    /**
     * INTERNAL 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    INTERNAL,
    /**
     * MERCHANT 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    MERCHANT,
    PAYER
}
