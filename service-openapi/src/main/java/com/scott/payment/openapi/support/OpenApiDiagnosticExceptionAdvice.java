package com.scott.payment.openapi.support;

import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.web.trace.HttpTrafficLoggingFilter;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    /**
     * 创建 OpenAPI 异常诊断日志处理器。
     *
     * @param diagnosticLogSupport 诊断日志摘要组件
     */
    public OpenApiDiagnosticExceptionAdvice(OpenApiDiagnosticLogSupport diagnosticLogSupport) {
        this.diagnosticLogSupport = diagnosticLogSupport;
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
    public CommonResult<Void> handleApiException(ApiException exception, HttpServletRequest request) {
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
    public CommonResult<Void> handleServiceException(ServiceException exception, HttpServletRequest request) {
        return handleBusinessException(exception, request);
    }

    /**
     * 统一记录 OpenAPI 业务失败上下文。
     *
     * @param exception 业务异常
     * @param request 当前 HTTP 请求
     * @return 统一错误响应
     */
    private CommonResult<Void> handleBusinessException(ServiceException exception, HttpServletRequest request) {
        CommonResult<Void> result = CommonResult.error(exception);
        request.setAttribute(OpenApiRequestAttributes.BUSINESS_CODE, result.getCode());
        request.setAttribute(OpenApiRequestAttributes.EXCEPTION_TYPE, exception.getClass().getSimpleName());
        String responseSummary = diagnosticLogSupport.responseEnvelopeSummary(result);
        request.setAttribute(OpenApiRequestAttributes.RESPONSE_PLAIN_SUMMARY, responseSummary);
        log.warn("event: OPENAPI_REQUEST_ABORTED stage=ERROR traceId: {} merchantId: {} method: {} path: {} apiVersion: {} interfaceType: {} platformCode: {} exceptionType: {} headerSummary: {} cipherRequestSummary: {} httpRequestDigest: {} httpRequestLength: {} httpRequestSummary: {} plainRequestSummary: {} plainResponseSummary: {} durationMs: {}",
                TraceContext.getTraceId(),
                merchantIdSafely(request),
                request.getMethod(),
                request.getRequestURI(),
                request.getAttribute(OpenApiRequestAttributes.API_VERSION),
                request.getAttribute(OpenApiRequestAttributes.INTERFACE_TYPE),
                result.getCode(),
                exception.getClass().getSimpleName(),
                diagnosticLogSupport.headerSummary(request),
                request.getAttribute(OpenApiRequestAttributes.REQUEST_CIPHER_SUMMARY),
                request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_DIGEST_ATTRIBUTE),
                request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_LENGTH_ATTRIBUTE),
                request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_SUMMARY_ATTRIBUTE),
                request.getAttribute(OpenApiRequestAttributes.REQUEST_PLAIN_SUMMARY),
                responseSummary,
                durationMs(request));
        return result;
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
