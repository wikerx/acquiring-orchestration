package com.scott.payment.channel.payment.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelResponseException
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道响应异常，位于 payment-channel-library 异常层，用于表达渠道响应缺失、格式错误或状态映射失败。
 * @status : create
 */
public class ChannelResponseException extends ChannelException {

    /**
     * 创建 ChannelResponseException 实例并注入其运行所需依赖。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     */
    public ChannelResponseException(String message) {
        super(message);
    }

    /**
     * 创建 ChannelResponseException 实例并注入其运行所需依赖。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     * @param cause cause 输入值，含义由调用方法名称和所属业务对象限定
     */
    public ChannelResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
