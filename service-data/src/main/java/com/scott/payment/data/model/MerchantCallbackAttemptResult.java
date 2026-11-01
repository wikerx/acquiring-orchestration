package com.scott.payment.data.model;

/**
 * 单次商户回调的安全结果快照，只携带状态推进和脱敏审计所需字段。
 *
 * @param success 商户是否按协议确认成功
 * @param failureCode 失败分类；成功时为空
 * @param httpStatus HTTP 状态码；未收到响应时为空
 * @param responseAudit 商户响应的固定确认词或不可逆摘要
 * @param errorMessage 可持久化的非敏感失败摘要
 * @param eventId 本次回调稳定事件号
 * @param requestAuditBody 已脱敏请求正文摘要
 */
public record MerchantCallbackAttemptResult(boolean success,
                                            MerchantCallbackFailureCode failureCode,
                                            Integer httpStatus,
                                            String responseAudit,
                                            String errorMessage,
                                            String eventId,
                                            String requestAuditBody) {

    /**
     * 创建商户已确认成功的回调结果。
     *
     * @param httpStatus 成功 HTTP 状态码
     * @param responseAudit 安全响应摘要
     * @param eventId 回调事件号
     * @param requestAuditBody 请求审计摘要
     * @return 成功结果
     */
    public static MerchantCallbackAttemptResult succeeded(Integer httpStatus,
                                                          String responseAudit,
                                                          String eventId,
                                                          String requestAuditBody) {
        return new MerchantCallbackAttemptResult(
                true, null, httpStatus, responseAudit, null, eventId, requestAuditBody);
    }

    /**
     * 创建带显式失败分类的回调结果。
     *
     * @param failureCode 失败分类
     * @param httpStatus HTTP 状态码；未收到响应时为空
     * @param responseAudit 安全响应摘要
     * @param errorMessage 非敏感失败摘要
     * @param eventId 回调事件号；请求构造前失败时为空
     * @param requestAuditBody 请求审计摘要
     * @return 失败结果
     */
    public static MerchantCallbackAttemptResult failed(MerchantCallbackFailureCode failureCode,
                                                       Integer httpStatus,
                                                       String responseAudit,
                                                       String errorMessage,
                                                       String eventId,
                                                       String requestAuditBody) {
        if (failureCode == null) {
            throw new IllegalArgumentException("merchant callback failure code is required");
        }
        return new MerchantCallbackAttemptResult(
                false, failureCode, httpStatus, responseAudit, errorMessage, eventId, requestAuditBody);
    }

    /**
     * 返回当前失败是否允许进入自动重试；成功结果始终返回 false。
     *
     * @return 自动重试标记
     */
    public boolean retryable() {
        return !success && failureCode != null && failureCode.isRetryable();
    }
}
