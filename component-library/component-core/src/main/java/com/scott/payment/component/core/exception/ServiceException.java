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

    private static final long serialVersionUID = 1L;

    private final String code;

    public ServiceException(String code, String message) {
        super(message);
        this.code = code;
    }

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
