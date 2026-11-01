package com.scott.payment.component.core.openapi.result;

/**
 * OpenAPI V1.3 结果事实的来源。
 *
 * <p>来源用于约束客户端请求、商户配置、平台业务、平台系统和渠道证据之间的归责边界。</p>
 */
public enum OpenApiResultOrigin {
    /**
     * CLIENT REQUEST 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CLIENT_REQUEST,
    /**
     * MERCHANT CONFIGURATION 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    MERCHANT_CONFIGURATION,
    /**
     * PLATFORM BUSINESS 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    PLATFORM_BUSINESS,
    /**
     * PLATFORM SYSTEM 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    PLATFORM_SYSTEM,
    /**
     * CHANNEL RESPONSE 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CHANNEL_RESPONSE,
    CHANNEL_TRANSPORT
}
