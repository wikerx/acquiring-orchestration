package com.scott.payment.data.model;

/**
 * 商户回调失败分类，显式区分可重试的不确定结果和应立即关闭的确定拒绝。
 */
public enum MerchantCallbackFailureCode {

    /**
     * 回调地址为空，任务配置无法通过重试自愈。
     */
    CALLBACK_URL_MISSING(false),

    /**
     * 回调目标违反公网出站策略或在建连时解析到受限地址。
     */
    TARGET_REJECTED(false),

    /**
     * 商户返回不可重试的 HTTP 客户端错误。
     */
    HTTP_CLIENT_REJECTED(false),

    /**
     * 商户返回限流、超时或服务端错误，后续允许重试。
     */
    HTTP_TRANSIENT(true),

    /**
     * HTTP 请求成功但确认词不符合回调协议，允许商户修复后重试。
     */
    ACKNOWLEDGEMENT_INVALID(true),

    /**
     * 建连、读取或连接池异常，渠道是否收到请求未知，按至少一次语义重试。
     */
    TRANSPORT_UNKNOWN(true),

    /**
     * 回调请求构造或本地协议处理失败，保留自动恢复机会。
     */
    PROTOCOL_ERROR(true);

    /**
     * 当前失败分类是否允许进入下一次自动投递。
     */
    private final boolean retryable;

    /**
     * 创建带固定重试语义的失败分类。
     *
     * @param retryable 是否允许自动重试
     */
    MerchantCallbackFailureCode(boolean retryable) {
        this.retryable = retryable;
    }

    /**
     * 返回该失败分类是否允许自动重试。
     *
     * @return true 表示结果不确定或可能恢复
     */
    public boolean isRetryable() {
        return retryable;
    }
}
