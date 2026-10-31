package com.scott.payment.component.core.openapi.result;

/**
 * OpenAPI 交易结果投影，承载公开五位码及标准化失败原因，不改变支付核心交易事实。
 *
 * @param resultCode OpenAPI V1.3 交易结果码
 * @param failureReasonCode 可公开的稳定失败原因码；非失败类结果为空
 * @param failureReasonMessage 可公开的安全失败说明；非失败类结果为空
 */
public record OpenApiTransactionProjection(OpenApiResultCode resultCode,
                                           String failureReasonCode,
                                           String failureReasonMessage) {

    /**
     * 按可信服务端上下文选择公开文案；调用方不得使用客户端 role 或 audience 参数决定展示级别。
     *
     * @param audience 已认证的展示对象
     * @param locale 已发布语言
     * @return 对应对象的安全文案
     */
    public String message(OpenApiMessageAudience audience, OpenApiMessageLocale locale) {
        // 该字段是支付核心保存的受控商户文案，不是渠道原文；其他对象必须重新从目录选择文案。
        if (audience == OpenApiMessageAudience.MERCHANT
                && locale == OpenApiMessageLocale.EN_US
                && failureReasonMessage != null
                && !failureReasonMessage.isBlank()) {
            return failureReasonMessage;
        }
        return OpenApiResultMessageCatalog.message(resultCode, audience, locale);
    }
}
