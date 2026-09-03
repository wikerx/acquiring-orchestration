package com.scott.payment.channel.payment.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelCallbackVerificationException
 * @date : 2026-08-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道回调验签失败异常，向入口层提供稳定原因分类，不包含密钥、签名原文或敏感报文。
 * @status : create
 */
public class ChannelCallbackVerificationException extends ChannelException {

    /**
     * 原因字段，保存 {@code ChannelCallbackVerificationException} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
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

    /** 不暴露协议细节的稳定验签失败分类。 */
    public enum Reason {
        /**
         * HEADER MISSING 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        HEADER_MISSING,
        /**
         * HEADER INVALID 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        HEADER_INVALID,
        /**
         * TIMESTAMP INVALID 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        TIMESTAMP_INVALID,
        /**
         * TIMESTAMP EXPIRED 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        TIMESTAMP_EXPIRED,
        /**
         * SECRET MISSING 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        SECRET_MISSING,
        /**
         * ALGORITHM UNSUPPORTED 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        ALGORITHM_UNSUPPORTED,
        /**
         * SIGNATURE INVALID 枚举值，表示当前枚举定义中的一个受控业务取值。
         * <p>
         * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
         * </p>
         */
        SIGNATURE_INVALID,
        INTERNAL_ERROR
    }
}
