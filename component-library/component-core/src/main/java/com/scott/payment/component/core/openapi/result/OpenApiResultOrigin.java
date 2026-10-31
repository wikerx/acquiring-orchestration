package com.scott.payment.component.core.openapi.result;

/**
 * OpenAPI V1.3 结果事实的来源。
 *
 * <p>来源用于约束客户端请求、商户配置、平台业务、平台系统和渠道证据之间的归责边界。</p>
 */
public enum OpenApiResultOrigin {
    CLIENT_REQUEST,
    MERCHANT_CONFIGURATION,
    PLATFORM_BUSINESS,
    PLATFORM_SYSTEM,
    CHANNEL_RESPONSE,
    CHANNEL_TRANSPORT
}
