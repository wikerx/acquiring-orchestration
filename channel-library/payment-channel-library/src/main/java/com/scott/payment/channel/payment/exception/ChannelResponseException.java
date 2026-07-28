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
     * 整理渠道响应异常，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 渠道适配库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     */
    public ChannelResponseException(String message) {
        super(message);
    }

    /**
     * 整理渠道响应异常，返回当前业务步骤需要的规范化结果。
     * <p>
     * 前置条件：调用方已准备 渠道适配库 当前步骤需要的输入对象和业务标识。
     * 该方法按所属类的业务边界执行必要的校验、转换、查询、写入或协作调用。
     * 异常边界：参数缺失、状态冲突、远程调用失败或持久化失败按当前模块约定处理。
     * </p>
     * @param message 待标准化的文本、编码或说明值，允许为空时由当前方法按默认规则处理
     * @param cause cause 输入值，参与 cause 的查询、校验、转换、写入或日志摘要
     */
    public ChannelResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
