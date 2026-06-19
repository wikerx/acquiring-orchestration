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
     * 根据标准结果码创建服务异常。
     *
     * @param result 标准结果码定义
     */
    public ServiceException(IResult result) {
        this(result.getCode(), result.getMessage());
    }

    /**
     * 获取异常响应码。
     *
     * @return 异常响应码
     */
    public String getCode() {
        return code;
    }
}
