package com.scott.payment.component.core.exception;

import com.scott.payment.component.core.result.IResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ServiceException
 * @date : 2026-05-28 18:16
 * @email : scott_x@163.com
 * @description : 服务内部业务异常
 * @status : create
 */
public class ServiceException extends RuntimeException {

    /**
     * 序列化版本号，用于保证异常对象在日志、RPC 或测试序列化场景下的兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 服务内部错误码，用于区分业务异常类型并交给统一异常处理器转换响应。
     */
    private final String code;

    /**
     * 创建服务内部业务异常。
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public ServiceException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 创建带原始异常原因的服务内部业务异常。
     *
     * @param code    错误码
     * @param message 错误消息
     * @param cause   原始异常原因
     */
    public ServiceException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    /**
     * 根据标准结果码创建服务异常。
     *
     * @param result 标准结果码定义
     */
    public ServiceException(IResult result) {
        this(result.getCode(), result.getMessage());
    }

    /**
     * 根据标准结果码和原始异常原因创建服务异常。
     *
     * @param result 标准结果码定义
     * @param cause  原始异常原因
     */
    public ServiceException(IResult result, Throwable cause) {
        this(result.getCode(), result.getMessage(), cause);
    }

    /**
     * 获取异常响应码。
     *
     * @return 异常响应码
     */
    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @return 处理后的业务结果或页面展示数据。
     */
    public String getCode() {
        return code;
    }
}
