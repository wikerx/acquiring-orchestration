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
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
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
        log.warn("Open API exception, code: {}, exceptionType: {}, messageLength: {}",
                exception.getCode(), exception.getClass().getSimpleName(), messageLength(exception.getMessage()));
        return errorResult(exception.getCode(), exception.getMessage());
    }

    /**
     * 处理服务内部业务异常。
     *
     * @param exception 服务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(ServiceException.class)
    public CommonResult<Void> handleServiceException(ServiceException exception) {
        log.warn("Service exception, code: {}, exceptionType: {}, messageLength: {}",
                exception.getCode(), exception.getClass().getSimpleName(), messageLength(exception.getMessage()));
        return errorResult(exception.getCode(), exception.getMessage());
    }

    /**
     * 兼容处理旧业务异常。
     *
     * @param exception 旧业务异常
     * @return 统一错误响应
     */
    @ExceptionHandler(BizException.class)
    public CommonResult<Void> handleBizException(BizException exception) {
        log.warn("Business exception, code: {}, exceptionType: {}, messageLength: {}",
                exception.getCode(), exception.getClass().getSimpleName(), messageLength(exception.getMessage()));
        return errorResult(exception.getCode(), exception.getMessage());
    }

    /**
     * 处理 Bean Validation 参数异常。
     *
     * @param exception 参数校验异常
     * @return 统一错误响应
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public CommonResult<Void> handleValidException(MethodArgumentNotValidException exception) {
        String fieldName = firstFieldName(exception.getBindingResult());
        log.warn("Request parameter validation failed, field: {}", fieldName);
        return CommonResult.error(ApiResultEnum.PARAM_INVALID.getCode(),
                resolveBindingValidationMessage(exception.getBindingResult()));
    }

    /**
     * 处理请求方法不支持异常。
     *
     * @param exception 请求方法异常
     * @return 统一错误响应
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public CommonResult<Void> handleMethodNotSupportedException(HttpRequestMethodNotSupportedException exception) {
        log.warn("Request method not supported, method: {}", exception.getMethod());
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
        String safeMessage = resolveRequestParameterMessage(exception);
        log.warn("Request parameter exception, type: {}, field: {}",
                exception.getClass().getSimpleName(), resolveRequestParameterField(exception));
        return CommonResult.error(ApiResultEnum.PARAM_INVALID.getCode(), safeMessage);
    }

    /**
     * 根据参数异常类型生成不包含 rejected value、请求正文或框架异常细节的对外提示。
     *
     * @param exception Spring Web 参数异常
     * @return 受控参数错误提示
     */
    private String resolveRequestParameterMessage(Exception exception) {
        if (exception instanceof BindException bindException) {
            return resolveBindingValidationMessage(bindException.getBindingResult());
        }
        if (exception instanceof ConstraintViolationException violationException) {
            return violationException.getConstraintViolations().stream()
                    .findFirst()
                    .map(this::resolveConstraintViolationMessage)
                    .orElse(ApiResultEnum.PARAM_INVALID.getMessage());
        }
        if (exception instanceof MissingServletRequestParameterException missingException) {
            return "Missing request parameter: " + missingException.getParameterName();
        }
        if (exception instanceof MethodArgumentTypeMismatchException mismatchException) {
            return "Invalid request parameter format: " + mismatchException.getName();
        }
        if (exception instanceof HttpMessageNotReadableException) {
            return "Invalid request body format.";
        }
        if (exception instanceof ServletRequestBindingException) {
            return "Request parameter binding failed.";
        }
        return ApiResultEnum.PARAM_INVALID.getMessage();
    }

    /**
     * 生成字段校验提示，仅使用服务端字段名和约束文案，不读取或拼接 rejected value。
     *
     * @param bindingResult Spring 参数绑定结果
     * @return 受控字段校验提示
     */
    private String resolveBindingValidationMessage(BindingResult bindingResult) {
        FieldError fieldError = bindingResult.getFieldError();
        if (fieldError != null) {
            return fieldError.getField() + ": " + validationText(fieldError.getDefaultMessage());
        }
        ObjectError objectError = bindingResult.getGlobalError();
        if (objectError != null) {
            return validationText(objectError.getDefaultMessage());
        }
        return ApiResultEnum.PARAM_INVALID.getMessage();
    }

    /**
     * 生成方法参数约束提示，不读取约束对应的实际参数值。
     *
     * @param violation Bean Validation 约束异常
     * @return 受控约束提示
     */
    private String resolveConstraintViolationMessage(ConstraintViolation<?> violation) {
        String propertyPath = violation.getPropertyPath() == null ? null : violation.getPropertyPath().toString();
        String message = validationText(violation.getMessage());
        return propertyPath == null || propertyPath.isBlank() ? message : propertyPath + ": " + message;
    }

    /**
     * 提取用于安全日志定位的字段名，不写入字段值或请求正文。
     *
     * @param exception Spring Web 参数异常
     * @return 字段名；无法确定时返回短横线
     */
    private String resolveRequestParameterField(Exception exception) {
        if (exception instanceof BindException bindException) {
            return firstFieldName(bindException.getBindingResult());
        }
        if (exception instanceof ConstraintViolationException violationException) {
            return violationException.getConstraintViolations().stream()
                    .findFirst()
                    .map(ConstraintViolation::getPropertyPath)
                    .map(Object::toString)
                    .orElse("-");
        }
        if (exception instanceof MissingServletRequestParameterException missingException) {
            return missingException.getParameterName();
        }
        if (exception instanceof MethodArgumentTypeMismatchException mismatchException) {
            return mismatchException.getName();
        }
        return "-";
    }

    /**
     * 提取首个校验失败字段名，用于日志定位且不包含字段值。
     *
     * @param bindingResult Spring 参数绑定结果
     * @return 字段名；不存在字段错误时返回短横线
     */
    private String firstFieldName(BindingResult bindingResult) {
        FieldError fieldError = bindingResult.getFieldError();
        return fieldError == null ? "-" : fieldError.getField();
    }

    /**
     * 规范化服务端声明的校验文案，避免空文案或换行污染响应与日志结构。
     *
     * @param message 校验注解或绑定器提供的约束文案
     * @return 单行、长度受控的校验文案
     */
    private String validationText(String message) {
        if (message == null || message.isBlank()) {
            return ApiResultEnum.PARAM_INVALID.getMessage();
        }
        String singleLine = message.replace('\r', ' ').replace('\n', ' ').trim();
        return singleLine.length() <= 160 ? singleLine : singleLine.substring(0, 160);
    }

    /**
     * 计算异常文案长度，日志只保留低敏感诊断信息而不记录原文。
     *
     * @param message 异常文案
     * @return 字符数；空值返回 0
     */
    private int messageLength(String message) {
        return message == null ? 0 : message.length();
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
     * 对外隐藏 F500 内部详情，其他业务错误保持原始响应。
     *
     * @param code    业务错误码
     * @param message 原始错误说明
     * @return 统一错误响应
     */
    private CommonResult<Void> errorResult(String code, String message) {
        if (ApiResultEnum.INTERNAL_SERVER_ERROR.getCode().equals(code)) {
            return CommonResult.error(ApiResultEnum.INTERNAL_SERVER_ERROR);
        }
        return CommonResult.error(code, message);
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
