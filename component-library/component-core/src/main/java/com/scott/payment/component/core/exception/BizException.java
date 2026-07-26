package com.scott.payment.component.core.exception;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BizException
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 业务异常定义
 * @status : create
 */
public class BizException extends RuntimeException {

    /**
     * 序列化版本号，用于保证异常对象在日志、RPC 或测试序列化场景下的兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 业务错误码，用于映射统一响应码和前端/调用方的错误处理逻辑。
     */
    private final String code;

    /**
     * 创建旧版业务异常。
     *
     * @param code    错误码
     * @param message 错误消息
     */
    public BizException(String code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 获取业务错误码。
     *
     * @return 业务错误码
     */
    public String getCode() {
        return code;
    }
}
