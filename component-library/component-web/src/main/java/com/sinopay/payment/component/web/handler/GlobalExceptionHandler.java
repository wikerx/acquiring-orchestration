package com.sinopay.payment.component.web.handler;

import com.sinopay.payment.component.core.constant.ErrorCode;
import com.sinopay.payment.component.core.exception.BizException;
import com.sinopay.payment.component.core.model.CommonResult;
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
    public CommonResult<Void> handleBizException(BizException exception) {
        log.warn("Business exception, code: {}, message: {}", exception.getCode(), exception.getMessage());
        return CommonResult.error(exception);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResult<Void> handleValidException(MethodArgumentNotValidException exception) {
        log.warn("Request parameter validation failed: {}", exception.getMessage());
        return CommonResult.error(ErrorCode.PARAM_INVALID, exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public CommonResult<Void> handleException(Exception exception) {
        log.error("System exception", exception);
        return CommonResult.error(ErrorCode.SYSTEM_ERROR, "system error");
    }
}
