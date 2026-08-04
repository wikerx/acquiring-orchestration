package com.scott.payment.component.web.handler;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.exception.BizException;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.exception.TransactionDataUnavailableException;
import com.scott.payment.component.core.model.CommonResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

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
        return CommonResult.error(ApiResultEnum.PARAM_INVALID.getCode(), exception.getMessage());
    }

    /**
     * 处理请求方法不支持异常。
     *
     * @param exception 请求方法异常
     * @return 统一错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public CommonResult<Void> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException exception) {
        log.warn("Request method not supported: {}", exception.getMessage());
        return CommonResult.error(ApiResultEnum.METHOD_NOT_ALLOWED);
    }

    /**
     * 处理路由不存在或静态资源不存在异常。
     * <p>
     * Spring Boot 3 默认会把未匹配路径包装成 NoResourceFoundException；开放 API 命名空间下如果未携带
     * Authorization，需要优先返回认证缺失，避免把商户非法请求误判为服务内部错误。
     *
     * @param exception 路由未命中异常
     * @param request   HTTP 请求
     * @return 统一错误响应
     */
    @ExceptionHandler({
            NoHandlerFoundException.class,
            NoResourceFoundException.class
    })

    public CommonResult<Void> handleRouteNotFoundException(Exception exception, HttpServletRequest request) {
        String requestPath = resolveRouteNotFoundPath(exception, request);
        log.warn("Request route not found, path: {}, exception: {}", requestPath, exception.getClass().getSimpleName());
        return OpenApiErrorResponseSupport.routeNotFound(request, requestPath);
    }

    /**
     * 处理常见请求参数异常。
     *
     * @param exception 参数异常
     * @return 统一错误响应
     */
    @ExceptionHandler({
            BindException.class,
            ConstraintViolationException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            ServletRequestBindingException.class,
            HttpMessageNotReadableException.class
    })

    public CommonResult<Void> handleRequestParameterException(Exception exception) {
        log.warn("Request parameter exception: {}", exception.getMessage());
        return CommonResult.error(ApiResultEnum.PARAM_INVALID.getCode(), exception.getMessage());
    }

    /**
     * 处理未捕获系统异常。
     *
     * @param exception 系统异常
     * @return 统一错误响应
     */
    @ExceptionHandler(Exception.class)
    public CommonResult<Void> handleException(Exception exception) {
        TransactionDataUnavailableException unavailable = findTransactionDataUnavailable(exception);
        if (unavailable != null) {
            log.warn("event: TRANSACTION_DATA_UNAVAILABLE logicalTable: {} quarter: {} ruleVersion: {} wrapperType: {}",
                    unavailable.getLogicalTable(), unavailable.getQuarter(), unavailable.getRuleVersion(),
                    exception.getClass().getSimpleName());
            return CommonResult.error(ApiResultEnum.TRANSACTION_DATA_UNAVAILABLE);
        }
        log.error("System exception", exception);
        return CommonResult.error(ApiResultEnum.INTERNAL_SERVER_ERROR);
    }

    /**
     * 沿 JDBC 和 ShardingSphere 的异常包装链查找结构化季度节点错误。
     *
     * @param exception Web 层捕获的顶层异常
     * @return 节点不可用异常；不存在时返回 null
     */
    private TransactionDataUnavailableException findTransactionDataUnavailable(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof TransactionDataUnavailableException unavailable) {
                return unavailable;
            }
            Throwable cause = current.getCause();
            if (cause == current) {
                break;
            }
            current = cause;
        }
        return null;
    }

    /**
     * 解析路由未命中异常中的原始请求地址。
     *
     * @param exception 路由未命中异常
     * @param request   HTTP 请求
     * @return 原始请求地址
     */
    private String resolveRouteNotFoundPath(Exception exception, HttpServletRequest request) {
        if (exception instanceof NoResourceFoundException noResourceFoundException) {
            return noResourceFoundException.getResourcePath();
        }
        if (exception instanceof NoHandlerFoundException noHandlerFoundException) {
            return noHandlerFoundException.getRequestURL();
        }
        return OpenApiErrorResponseSupport.resolveOriginalRequestUri(request);
    }
}
