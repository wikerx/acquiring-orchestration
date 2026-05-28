package com.sinopay.payment.component.web.handler;

import com.sinopay.payment.component.core.constant.ErrorCode;
import com.sinopay.payment.component.core.exception.BizException;
import com.sinopay.payment.component.core.model.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : GlobalExceptionHandler
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 全局异常处理器
 * @status : create
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResult<Void> handleBizException(BizException exception) {
        log.warn("Business exception, code: {}, message: {}", exception.getCode(), exception.getMessage());
        return ApiResult.fail(exception.getCode(), exception.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiResult<Void> handleValidException(MethodArgumentNotValidException exception) {
        log.warn("Request parameter validation failed: {}", exception.getMessage());
        return ApiResult.fail(ErrorCode.PARAM_INVALID, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ApiResult<Void> handleException(Exception exception) {
        log.error("System exception", exception);
        return ApiResult.fail(ErrorCode.SYSTEM_ERROR, "system error");
    }
}
