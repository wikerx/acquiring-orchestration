package com.scott.payment.component.core.openapi.result;

/**
 * OpenAPI V1.3 已发布的文案语言。
 *
 * <p>未知语言由协议边界回退到英文，不得影响返回码、交易状态或结果确定性。</p>
 */
public enum OpenApiMessageLocale {
    /**
     * ZH CN 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    ZH_CN,
    EN_US
}
