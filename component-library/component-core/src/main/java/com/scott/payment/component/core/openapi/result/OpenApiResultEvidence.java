package com.scott.payment.component.core.openapi.result;

/**
 * 支撑 OpenAPI V1.3 结果投影的事实证据类型。
 *
 * <p>证据类型用于区分已确认终态、非终态和渠道结果未确认等事实，避免仅凭异常文案猜测公开码。</p>
 */
public enum OpenApiResultEvidence {
    /**
     * REQUEST COMPLETED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    REQUEST_COMPLETED,
    /**
     * REQUEST ACCEPTED 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    REQUEST_ACCEPTED,
    /**
     * PARTIAL RESULT 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    PARTIAL_RESULT,
    /**
     * VALIDATION REJECTION 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    VALIDATION_REJECTION,
    /**
     * AUTHENTICATION REJECTION 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    AUTHENTICATION_REJECTION,
    /**
     * AUTHORIZATION REJECTION 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    AUTHORIZATION_REJECTION,
    /**
     * CONFIGURATION REJECTION 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CONFIGURATION_REJECTION,
    /**
     * CONFIRMED TRANSACTION SUCCESS 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CONFIRMED_TRANSACTION_SUCCESS,
    /**
     * CONFIRMED TRANSACTION FAILURE 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CONFIRMED_TRANSACTION_FAILURE,
    /**
     * NON TERMINAL TRANSACTION 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    NON_TERMINAL_TRANSACTION,
    /**
     * CONFIRMED CHANNEL RESPONSE 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    CONFIRMED_CHANNEL_RESPONSE,
    /**
     * PLATFORM FAILURE 枚举值，表示当前枚举定义中的一个受控业务取值。
     */
    PLATFORM_FAILURE,
    UNCONFIRMED_CHANNEL_OUTCOME
}
