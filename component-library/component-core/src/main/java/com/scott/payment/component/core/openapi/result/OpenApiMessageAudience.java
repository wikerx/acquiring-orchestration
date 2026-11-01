package com.scott.payment.component.core.openapi.result;

/**
 * OpenAPI 返回文案的可信展示对象。
 *
 * <p>展示对象必须由服务端入口和认证上下文确定，禁止直接采信客户端提交的角色或 audience 参数。</p>
 */
public enum OpenApiMessageAudience {
    /**
     * INTERNAL 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    INTERNAL,
    /**
     * MERCHANT 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    MERCHANT,
    PAYER
}
