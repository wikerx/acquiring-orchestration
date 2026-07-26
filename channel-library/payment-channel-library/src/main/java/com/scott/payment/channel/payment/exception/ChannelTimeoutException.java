package com.scott.payment.channel.payment.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ChannelTimeoutException
 * @date : 2026-07-12 00:00
 * @email : scott_x@163.com
 * @description : 渠道超时异常，位于 payment-channel-library 异常层，用于表达渠道请求超时且结果未知的场景。
 * @status : create
 */
public class ChannelTimeoutException extends ChannelException {

    /**
     * 创建 ChannelTimeoutException 实例并注入其运行所需依赖。
     * <p>
     * 层级边界：渠道适配层；输入来源、输出结构和异常语义由 ChannelTimeoutException 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     */
    public ChannelTimeoutException(String message) {
        super(message);
    }

    /**
     * 创建 ChannelTimeoutException 实例并注入其运行所需依赖。
     * <p>
     * 层级边界：渠道适配层；输入来源、输出结构和异常语义由 ChannelTimeoutException 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param message 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     * @param cause cause 输入值，含义由调用方法名称和所属业务对象限定
     */
    public ChannelTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
