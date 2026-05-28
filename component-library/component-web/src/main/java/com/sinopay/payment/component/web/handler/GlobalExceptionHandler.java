package com.sinopay.payment.component.web.handler;

import com.sinopay.payment.component.core.enums.ApiCoResultEnum;
import com.sinopay.payment.component.core.exception.ApiException;
import com.sinopay.payment.component.core.exception.BizException;
import com.sinopay.payment.component.core.exception.ServiceException;
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

    /**
     * 处理开放 API 业务异常。
     *
     * @param exception 开放 API 异常
     * @return 统一错误响应
     */
    @ExceptionHandler(ApiException.class)
    public CommonResult<Void> handleApiException(ApiException exception) {
        log.warn("Open API exception, code: {}, message: {}", exception.getCode(), exception.getMessage());
        return CommonResult.error(exception);
    }

    /**
     * 处理服务内部业务异常。
     *
     * @param exception 服务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(ServiceException.class)
    public CommonResult<Void> handleServiceException(ServiceException exception) {
        log.warn("Service exception, code: {}, message: {}", exception.getCode(), exception.getMessage());
        return CommonResult.error(exception);
    }

    /**
     * 兼容处理旧业务异常。
     *
     * @param exception 旧业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BizException.class)
    public CommonResult<Void> handleBizException(BizException exception) {
        log.warn("Business exception, code: {}, message: {}", exception.getCode(), exception.getMessage());
        return CommonResult.error(exception);
    }

    /**
     * 处理 Bean Validation 参数异常。
     *
     * @param exception 参数校验异常
     * @return 统一错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResult<Void> handleValidException(MethodArgumentNotValidException exception) {
        log.warn("Request parameter validation failed: {}", exception.getMessage());
        return CommonResult.error(ApiCoResultEnum.CO_REQUIRED_PARAMETER_INVALID.getCode(), exception.getMessage());
    }

    /**
     * 处理未捕获系统异常。
     *
     * @param exception 系统异常
     * @return 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public CommonResult<Void> handleException(Exception exception) {
        log.error("System exception", exception);
        return CommonResult.error(ApiCoResultEnum.CO_INTERNAL_SERVER_ERROR);
    }
}
