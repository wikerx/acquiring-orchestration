package com.global.payment.component.web.handler;

import com.global.payment.component.core.constant.ErrorCode;
import com.global.payment.component.core.exception.BizException;
import com.global.payment.component.core.model.ApiResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBizException(BizException exception) {
        return ApiResult.fail(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValidException(MethodArgumentNotValidException exception) {
        return ApiResult.fail(ErrorCode.PARAM_INVALID, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleException(Exception exception) {
        return ApiResult.fail(ErrorCode.SYSTEM_ERROR, "system error");
    }
}

