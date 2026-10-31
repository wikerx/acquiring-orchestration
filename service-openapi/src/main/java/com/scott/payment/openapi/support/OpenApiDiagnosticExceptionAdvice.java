package com.scott.payment.openapi.support;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.exception.RateLimitExceededException;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.openapi.result.OpenApiMessageAudience;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.web.handler.GlobalExceptionHandler;
import com.scott.payment.component.web.trace.HttpTrafficLoggingFilter;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.security.MerchantIpWhitelistAccessDeniedException;
import com.scott.payment.openapi.result.OpenApiErrorMapper;
import com.scott.payment.openapi.result.OpenApiErrorProjection;
import com.scott.payment.openapi.result.OpenApiResponseFactory;
import com.scott.payment.component.core.openapi.result.OpenApiResultCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiDiagnosticExceptionAdvice
 * @date : 2026-07-26 17:35
 * @email : scott_x@163.com
 * @description : OpenAPI 异常诊断日志处理器，位于 service-openapi Web 支撑层，记录认证、解密、校验和下游调用失败时的请求响应摘要。
 * @status : create
 */
@Slf4j
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class OpenApiDiagnosticExceptionAdvice {

    /**
     * OpenAPI 诊断日志支撑组件。
     */
    private final OpenApiDiagnosticLogSupport diagnosticLogSupport;

    /** 非商户 OpenAPI 请求继续使用系统原有异常契约，避免浏览器、回调和内部接口被五位码迁移污染。 */
    private final GlobalExceptionHandler legacyExceptionHandler;

    /**
     * 创建 OpenAPI 异常诊断日志处理器。
     *
     * @param diagnosticLogSupport 诊断日志摘要组件
     * @param legacyExceptionHandler 系统原有全局异常处理器
     */
    public OpenApiDiagnosticExceptionAdvice(OpenApiDiagnosticLogSupport diagnosticLogSupport,
                                            GlobalExceptionHandler legacyExceptionHandler) {
        this.diagnosticLogSupport = diagnosticLogSupport;
        this.legacyExceptionHandler = legacyExceptionHandler;
    }

    /**
     * 处理 OpenAPI 业务异常并输出可排障的失败响应摘要。
     * <p>
     * 拦截器、请求体解密或下游调用阶段抛出的 ApiException 可能不会进入正常响应加密 Advice，
     * 因此这里补充 OPENAPI_REQUEST_ABORTED 日志；响应仍保持原有 CommonResult 错误结构。
     * </p>
     * @param exception OpenAPI 业务异常
     * @param request 当前 HTTP 请求
     * @return 统一错误响应
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<CommonResult<Void>> handleApiException(ApiException exception, HttpServletRequest request) {
        return handleBusinessException(exception, request);
    }

    /** OpenAPI 限流响应返回 HTTP 429 和动态 Retry-After，响应体仍使用 V1.3 A4001。 */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<CommonResult<Void>> handleRateLimitExceeded(
            RateLimitExceededException exception, HttpServletRequest request) {
        return handleBusinessException(exception, request);
    }

    /**
     * 处理 OpenAPI 服务异常并输出可排障的失败响应摘要。
     * <p>
     * service-payment 调用失败、本地降级服务校验失败等场景可能抛出 ServiceException；
     * 这里保持原错误响应协议，仅补充 OpenAPI 失败闭环日志。
     * </p>
     * @param exception 服务业务异常
     * @param request 当前 HTTP 请求
     * @return 统一错误响应
     */
    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<CommonResult<Void>> handleServiceException(ServiceException exception, HttpServletRequest request) {
        return handleBusinessException(exception, request);
    }

    /** Bean Validation、类型转换和约束异常统一返回 A1001。 */
    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class,
            ConstraintViolationException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<CommonResult<Void>> handleValidationException(
            Exception exception, HttpServletRequest request) {
        return handleFrameworkException(exception, request,
                OpenApiResultCode.PARAM_INVALID);
    }

    /** 缺少查询参数或必要 Servlet 绑定值统一返回 A1002。 */
    @ExceptionHandler({MissingServletRequestParameterException.class, ServletRequestBindingException.class})
    public ResponseEntity<CommonResult<Void>> handleMissingParameterException(
            Exception exception, HttpServletRequest request) {
        return handleFrameworkException(exception, request,
                OpenApiResultCode.PARAM_MISSING);
    }

    /** 外层 JSON 或 HTTP 请求结构无法解析时返回 A1005。 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonResult<Void>> handleUnreadableRequest(
            HttpMessageNotReadableException exception, HttpServletRequest request) {
        return handleFrameworkException(exception, request,
                OpenApiResultCode.BAD_REQUEST);
    }

    /** HTTP 方法不受支持时返回 A1006，并保留当前版本既有 HTTP 状态。 */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonResult<Void>> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
        return handleFrameworkException(exception, request,
                OpenApiResultCode.METHOD_NOT_ALLOWED);
    }

    /** Content-Type 不受支持时返回 A1007，并保留当前版本既有 HTTP 状态。 */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<CommonResult<Void>> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
        return handleFrameworkException(exception, request,
                OpenApiResultCode.CONTENT_TYPE_NOT_SUPPORTED);
    }

    /** 请求体超过服务端限制时返回 A1008，并保留当前版本既有 HTTP 状态。 */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CommonResult<Void>> handleRequestTooLarge(
            MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return handleFrameworkException(exception, request,
                OpenApiResultCode.REQUEST_TOO_LARGE);
    }

    /** OpenAPI 路由不存在时返回 A3002，并保留当前版本既有 HTTP 状态。 */
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<CommonResult<Void>> handleNotFound(
            Exception exception, HttpServletRequest request) {
        return handleFrameworkException(exception, request,
                OpenApiResultCode.API_NOT_FOUND);
    }

    /** 未分类运行时异常在商户 OpenAPI 上失败关闭为 F2000，不暴露异常原文。 */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResult<Void>> handleUnexpectedException(
            Exception exception, HttpServletRequest request) {
        return handleFrameworkException(exception, request,
                OpenApiResultCode.SYSTEM_ERROR);
    }

    /**
     * 统一记录 OpenAPI 业务失败上下文。
     *
     * @param exception 业务异常
     * @param request 当前 HTTP 请求
     * @return 统一错误响应
     */
    private ResponseEntity<CommonResult<Void>> handleBusinessException(ServiceException exception,
                                                                       HttpServletRequest request) {
        OpenApiMessageAudience audience = publicAudience(request);
        if (audience == null) {
            CommonResult<Void> legacyResult = CommonResult.error(exception);
            recordFailure(exception, request, legacyResult);
            return businessResponse(exception, legacyResult);
        }
        OpenApiErrorProjection projection = OpenApiErrorMapper.project(exception);
        CommonResult<Void> result = OpenApiResponseFactory.error(projection, audience);
        recordFailure(exception, request, result, projection);
        return businessResponse(exception, result);
    }

    /**
     * 保持既有 HTTP 契约：普通业务异常返回 HTTP 200，限流继续返回 HTTP 429 和 Retry-After。
     * 业务码不参与 HTTP 状态推导，避免后续增加返回码时意外改变传输层行为。
     */
    private ResponseEntity<CommonResult<Void>> businessResponse(ServiceException exception,
                                                                 CommonResult<Void> result) {
        HttpHeaders headers = new HttpHeaders();
        if (exception instanceof RateLimitExceededException rateLimitExceededException) {
            headers.set(HttpHeaders.RETRY_AFTER,
                    String.valueOf(rateLimitExceededException.getRetryAfterSeconds()));
            return new ResponseEntity<>(result, headers, org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        }
        return ResponseEntity.ok(result);
    }

    /** 记录异常响应的闭环诊断信息，公开响应已在调用方完成安全投影。 */
    private void recordFailure(Throwable exception,
                               HttpServletRequest request,
                               CommonResult<Void> result) {
        recordFailure(exception, request, result, null);
    }

    /** 记录公开码和结构化来源证据，不记录异常原文、请求值或未过滤渠道报文。 */
    private void recordFailure(Throwable exception,
                               HttpServletRequest request,
                               CommonResult<Void> result,
                               OpenApiErrorProjection projection) {
        request.setAttribute(OpenApiRequestAttributes.BUSINESS_CODE, result.getCode());
        request.setAttribute(OpenApiRequestAttributes.EXCEPTION_TYPE, exception.getClass().getSimpleName());
        String responseSummary = diagnosticLogSupport.responseEnvelopeSummary(result);
        request.setAttribute(OpenApiRequestAttributes.RESPONSE_PLAIN_SUMMARY, responseSummary);
        log.warn("OpenAPI请求异常终止, event: OPENAPI_REQUEST_ABORTED, stage: ERROR, traceId: {}, merchantId: {}, method: {}, path: {}, apiVersion: {}, interfaceType: {}, platformCode: {}, internalCode: {}, resultPhase: {}, resultOrigin: {}, requestSent: {}, outcomeUncertain: {}, resultEvidence: {}, exceptionType: {}, requestIp: {}, headerSummary: {}, cipherRequestSummary: {}, httpRequestDigest: {}, httpRequestLength: {}, httpRequestSummary: {}, plainRequestSummary: {}, plainResponseSummary: {}, durationMs: {}",
                TraceContext.getTraceId(),
                merchantIdSafely(request),
                request.getMethod(),
                request.getRequestURI(),
                request.getAttribute(OpenApiRequestAttributes.API_VERSION),
                request.getAttribute(OpenApiRequestAttributes.INTERFACE_TYPE),
                result.getCode(),
                projection == null ? null : projection.internalCode(),
                projection == null ? null : projection.phase(),
                projection == null ? null : projection.origin(),
                projection == null ? null : projection.requestSent(),
                projection == null ? null : projection.outcomeUncertain(),
                projection == null ? null : projection.evidence(),
                exception.getClass().getSimpleName(),
                deniedRequestIp(exception),
                diagnosticLogSupport.headerSummary(request),
                request.getAttribute(OpenApiRequestAttributes.REQUEST_CIPHER_SUMMARY),
                request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_DIGEST_ATTRIBUTE),
                request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_LENGTH_ATTRIBUTE),
                request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_SUMMARY_ATTRIBUTE),
                request.getAttribute(OpenApiRequestAttributes.REQUEST_PLAIN_SUMMARY),
                responseSummary,
                durationMs(request));
    }

    /** 将 Spring MVC 框架异常按请求命名空间投影，新旧契约互不污染。 */
    private ResponseEntity<CommonResult<Void>> handleFrameworkException(Exception exception,
                                                                        HttpServletRequest request,
                                                                        OpenApiResultCode openApiCode) {
        OpenApiMessageAudience audience = publicAudience(request);
        if (audience == null) {
            CommonResult<Void> legacyResult = legacyFrameworkResult(exception, request);
            recordFailure(exception, request, legacyResult);
            return ResponseEntity.ok(legacyResult);
        }
        OpenApiErrorProjection projection = OpenApiErrorProjection.fromResultCode(
                openApiCode, frameworkFieldPath(exception));
        CommonResult<Void> result = OpenApiResponseFactory.error(projection, audience);
        recordFailure(exception, request, result, projection);
        return ResponseEntity.ok(result);
    }

    /**
     * 从 Spring MVC 异常的结构化属性提取字段路径，不读取 rejectedValue 或异常 message。
     * 该路径还会经过文案目录的长度与字符白名单；无法可靠识别时返回静态安全文案。
     */
    private String frameworkFieldPath(Exception exception) {
        if (exception instanceof MethodArgumentNotValidException validException) {
            return validException.getBindingResult().getFieldError() == null
                    ? null : validException.getBindingResult().getFieldError().getField();
        }
        if (exception instanceof BindException bindException) {
            return bindException.getBindingResult().getFieldError() == null
                    ? null : bindException.getBindingResult().getFieldError().getField();
        }
        if (exception instanceof ConstraintViolationException violationException) {
            return violationException.getConstraintViolations().stream()
                    .findFirst()
                    .map(violation -> constraintFieldPath(violation.getPropertyPath()))
                    .orElse(null);
        }
        if (exception instanceof MethodArgumentTypeMismatchException mismatchException) {
            return mismatchException.getName();
        }
        if (exception instanceof MissingServletRequestParameterException missingParameterException) {
            return missingParameterException.getParameterName();
        }
        if (exception instanceof MissingRequestHeaderException missingHeaderException) {
            return missingHeaderException.getHeaderName();
        }
        if (exception instanceof MissingPathVariableException missingPathVariableException) {
            return missingPathVariableException.getVariableName();
        }
        return null;
    }

    /** 只保留 Bean Validation 的属性节点，避免将控制器方法名等实现细节返回给商户。 */
    private String constraintFieldPath(Path propertyPath) {
        if (propertyPath == null) {
            return null;
        }
        List<String> properties = new ArrayList<>();
        String parameter = null;
        for (Path.Node node : propertyPath) {
            if (node.getKind() == ElementKind.PROPERTY && node.getName() != null) {
                properties.add(node.getName());
            } else if (node.getKind() == ElementKind.PARAMETER && node.getName() != null) {
                parameter = node.getName();
            }
        }
        return properties.isEmpty() ? parameter : String.join(".", properties);
    }

    /**
     * 非 `/api/rest/**` 请求复用原全局处理器的精确行为，包括旧码、受控参数文案和特殊异常识别。
     */
    private CommonResult<Void> legacyFrameworkResult(Exception exception, HttpServletRequest request) {
        if (exception instanceof MethodArgumentNotValidException validException) {
            return legacyExceptionHandler.handleValidException(validException);
        }
        if (exception instanceof HttpRequestMethodNotSupportedException methodException) {
            return legacyExceptionHandler.handleMethodNotSupportedException(methodException);
        }
        if (exception instanceof NoHandlerFoundException || exception instanceof NoResourceFoundException) {
            return legacyExceptionHandler.handleRouteNotFoundException(exception, request);
        }
        if (exception instanceof BindException
                || exception instanceof ConstraintViolationException
                || exception instanceof MissingServletRequestParameterException
                || exception instanceof MethodArgumentTypeMismatchException
                || exception instanceof ServletRequestBindingException
                || exception instanceof HttpMessageNotReadableException) {
            return legacyExceptionHandler.handleRequestParameterException(exception);
        }
        return legacyExceptionHandler.handleException(exception);
    }

    /**
     * 根据服务端路由命名空间固定展示对象；渠道回调、内部通知和 3DS HTML bridge 保持原协议。
     */
    private OpenApiMessageAudience publicAudience(HttpServletRequest request) {
        if (request == null || request.getRequestURI() == null) {
            return null;
        }
        String requestUri = request.getRequestURI();
        if (requestUri.startsWith("/api/rest/")) {
            return OpenApiMessageAudience.MERCHANT;
        }
        if (requestUri.startsWith("/checkout/api/v1/") && !requestUri.endsWith("/3ds/bridge")) {
            return OpenApiMessageAudience.PAYER;
        }
        return null;
    }

    private String deniedRequestIp(Throwable exception) {
        if (exception instanceof MerchantIpWhitelistAccessDeniedException deniedException) {
            return deniedException.getRequestIp() == null ? "unknown" : deniedException.getRequestIp();
        }
        return null;
    }

    /**
     * 计算当前 OpenAPI 请求从进入拦截器到失败响应生成的耗时。
     *
     * @param request 当前 HTTP 请求
     * @return 毫秒耗时，缺少开始时间时返回 null
     */
    private Long durationMs(HttpServletRequest request) {
        Object startValue = request == null ? null : request.getAttribute(OpenApiRequestAttributes.REQUEST_START_NANOS);
        if (!(startValue instanceof Long startNanos)) {
            return null;
        }
        return (System.nanoTime() - startNanos) / 1_000_000L;
    }

    /**
     * 从已经完成认证的 OpenAPI 请求上下文中读取商户号。
     * <p>
     * 认证、解密或参数校验失败时，请求可能尚未绑定商户上下文；此时返回 null，
     * 日志仍可通过 traceId、请求路径、header 摘要和 body 摘要定位问题。
     * </p>
     * @param request 当前 HTTP 请求
     * @return 已验证商户号，认证前失败时返回 null
     */
    private String merchantIdSafely(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(OpenApiRequestAttributes.REQUEST_HEADER);
        if (value instanceof OpenApiRequestHeaderDTO headerDTO) {
            return headerDTO.getMerchantId();
        }
        return null;
    }
}
