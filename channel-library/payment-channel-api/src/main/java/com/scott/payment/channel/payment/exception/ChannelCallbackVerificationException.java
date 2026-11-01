package com.scott.payment.channel.payment.exception;

/**
 * 渠道回调验签失败异常，向入口层提供稳定原因分类，不包含密钥、签名原文或敏感报文。
 */
public class ChannelCallbackVerificationException extends ChannelException {

    /**
     * 不暴露签名和密钥细节的稳定验签失败分类。
     */
    private final Reason reason;

    /**
     * 构造不携带底层异常的渠道回调验签失败异常。
     *
     * @param reason 稳定失败分类，用于调用方执行受控错误映射
     * @param message 不包含签名原文、密钥或完整回调报文的安全说明
     */
    public ChannelCallbackVerificationException(Reason reason, String message) {
        this(reason, message, null);
    }

    /**
     * 构造携带底层原因的渠道回调验签失败异常。
     *
     * @param reason 稳定失败分类，用于调用方执行受控错误映射
     * @param message 不包含签名原文、密钥或完整回调报文的安全说明
     * @param cause 底层异常，仅供服务端诊断，不得直接返回调用方
     */
    public ChannelCallbackVerificationException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    /**
     * 查询原因；筛选条件、分页上限和数据范围由方法参数共同限定。
     * <p>
     * 只读操作；实现必须沿用 渠道适配库 既有权限、数据范围和空结果约定。
     * </p>
     * @return 查询得到的业务对象、分页结果或空结果
     */
    public Reason getReason() {
        return reason;
    }

    /**
     * 不暴露协议细节的稳定验签失败分类。
     */
    public enum Reason {
        /**
         * HEADER MISSING 枚举值，表示当前枚举定义中的一个受控业务取值。
         */
        HEADER_MISSING,
        /**
         * HEADER INVALID 枚举值，表示当前枚举定义中的一个受控业务取值。
         */
        HEADER_INVALID,
        /**
         * TIMESTAMP INVALID 枚举值，表示当前枚举定义中的一个受控业务取值。
         */
        TIMESTAMP_INVALID,
        /**
         * TIMESTAMP EXPIRED 枚举值，表示当前枚举定义中的一个受控业务取值。
         */
        TIMESTAMP_EXPIRED,
        /**
         * SECRET MISSING 枚举值，表示当前枚举定义中的一个受控业务取值。
         */
        SECRET_MISSING,
        /**
         * ALGORITHM UNSUPPORTED 枚举值，表示当前枚举定义中的一个受控业务取值。
         */
        ALGORITHM_UNSUPPORTED,
        /**
         * SIGNATURE INVALID 枚举值，表示当前枚举定义中的一个受控业务取值。
         */
        SIGNATURE_INVALID,
        INTERNAL_ERROR
    }
}
