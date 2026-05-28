package com.scott.payment.component.core.exception;

import com.scott.payment.component.core.result.IResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ApiException
 * @date : 2026-05-28 18:16
 * @email : scott_x@163.com
 * @description : 对外 API 业务异常
 * @status : create
 */
public class ApiException extends ServiceException {

    private static final long serialVersionUID = 1L;

    public ApiException(String code, String message) {
        super(code, message);
    }

    public ApiException(IResult result) {
        super(result);
    }

    public ApiException(IResult result, String detail) {
        super(result.getCode(), result.getMessage() + ":" + detail);
    }
}
