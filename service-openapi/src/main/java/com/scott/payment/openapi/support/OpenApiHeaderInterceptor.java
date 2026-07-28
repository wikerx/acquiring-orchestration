package com.scott.payment.openapi.support;

import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.web.trace.HttpTrafficLoggingFilter;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Collections;
import java.util.List;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiHeaderInterceptor
 * @date : 2026-05-28 16:17
 * @email : scott_x@163.com
 * @description : Open API Header Interceptor 拦截组件，位于 商户开放接口服务，处理请求链路中的 traceId、鉴权、来源信息、上下文绑定或安全校验。
 * @status : create
 */
@Component
@Slf4j
public class OpenApiHeaderInterceptor implements HandlerInterceptor {

    /**
     * 请求头提取器，负责校验必填请求头并完成商户 JWT 验签。
     */
    private final OpenApiRequestHeaderExtractor headerExtractor;

    /**
     * OpenAPI 诊断日志支撑组件，负责生成请求头、密文和响应摘要。
     */
    private final OpenApiDiagnosticLogSupport diagnosticLogSupport;

    /**
     * 创建开放接口请求头拦截器。
     *
     * @param headerExtractor 请求头提取器
     */
    public OpenApiHeaderInterceptor(OpenApiRequestHeaderExtractor headerExtractor,
                                    OpenApiDiagnosticLogSupport diagnosticLogSupport) {
        this.headerExtractor = headerExtractor;
        this.diagnosticLogSupport = diagnosticLogSupport;
    }

    /**
     * 在进入控制器前完成开放 API 请求头校验。
     *
     * @param request  HTTP 请求
     * @param response HTTP 响应
     * @param handler  MVC 处理器
     * @return 是否继续执行请求
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        VerificationAndProcessing annotation = AnnotationUtils.findAnnotation(
                handlerMethod.getMethod(),
                VerificationAndProcessing.class
        );
        if (annotation == null || !annotation.requiredHeader()) {
            return true;
        }
        long startNanos = System.nanoTime();
        request.setAttribute(OpenApiRequestAttributes.REQUEST_START_NANOS, startNanos);
        request.setAttribute(OpenApiRequestAttributes.API_VERSION, apiVersion(request.getRequestURI()));
        request.setAttribute(OpenApiRequestAttributes.INTERFACE_TYPE, interfaceType(request.getRequestURI()));
        log.info("event: OPENAPI_REQUEST_ENTER stage=ACCEPT traceId: {} method: {} path: {} queryKeys: {} apiVersion: {} interfaceType: {} clientIp: {} contentType: {} contentLength: {} headerSummary: {} httpRequestDigest: {} httpRequestLength: {} httpRequestSummary: {}",
                TraceContext.getTraceId(),
                request.getMethod(),
                request.getRequestURI(),
                queryKeys(request),
                request.getAttribute(OpenApiRequestAttributes.API_VERSION),
                request.getAttribute(OpenApiRequestAttributes.INTERFACE_TYPE),
                clientIp(request),
                request.getContentType(),
                request.getContentLengthLong(),
                diagnosticLogSupport.headerSummary(request),
                request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_DIGEST_ATTRIBUTE),
                request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_LENGTH_ATTRIBUTE),
                request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_SUMMARY_ATTRIBUTE));
        OpenApiRequestHeaderDTO headerDTO = headerExtractor.extract(request, annotation.requiredHeaders());
        request.setAttribute(OpenApiRequestAttributes.REQUEST_HEADER, headerDTO);
        return true;
    }

    /**
     * 在请求结束时输出 OpenAPI 全阶段闭环日志。
     * <p>
     * 结束日志聚合请求头摘要、密文请求摘要、解密后明文摘要、响应明文摘要和响应密文摘要；
     * Authorization、JWT、完整密文、卡号和安全码只允许以脱敏或摘要形式出现。
     * </p>
     *
     * @param request   HTTP 请求
     * @param response  HTTP 响应
     * @param handler   MVC 处理器
     * @param exception Spring MVC 传递的异常对象，正常完成时为空
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        Object startValue = request.getAttribute(OpenApiRequestAttributes.REQUEST_START_NANOS);
        if (!(startValue instanceof Long startNanos)) {
            return;
        }
        OpenApiRequestHeaderDTO headerDTO = (OpenApiRequestHeaderDTO) request.getAttribute(OpenApiRequestAttributes.REQUEST_HEADER);
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        log.info("event: OPENAPI_REQUEST_END stage=FINISH traceId: {} merchantId: {} method: {} path: {} queryKeys: {} apiVersion: {} interfaceType: {} clientIp: {} httpStatus: {} platformCode: {} durationMs: {} exceptionType: {} headerSummary: {} httpRequestDigest: {} httpRequestLength: {} httpRequestSummary: {} cipherRequestSummary: {} plainRequestSummary: {} plainResponseSummary: {} cipherResponseSummary: {}",
                TraceContext.getTraceId(),
                headerDTO == null ? null : headerDTO.getMerchantId(),
                request.getMethod(),
                request.getRequestURI(),
                queryKeys(request),
                request.getAttribute(OpenApiRequestAttributes.API_VERSION),
                request.getAttribute(OpenApiRequestAttributes.INTERFACE_TYPE),
                headerDTO == null ? clientIp(request) : headerDTO.getClientIp(),
                response.getStatus(),
                request.getAttribute(OpenApiRequestAttributes.BUSINESS_CODE),
                durationMs,
                exception == null
                        ? request.getAttribute(OpenApiRequestAttributes.EXCEPTION_TYPE)
                        : exception.getClass().getSimpleName(),
                diagnosticLogSupport.headerSummary(request),
                request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_DIGEST_ATTRIBUTE),
                request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_LENGTH_ATTRIBUTE),
                request.getAttribute(HttpTrafficLoggingFilter.REQUEST_BODY_SUMMARY_ATTRIBUTE),
                request.getAttribute(OpenApiRequestAttributes.REQUEST_CIPHER_SUMMARY),
                request.getAttribute(OpenApiRequestAttributes.REQUEST_PLAIN_SUMMARY),
                request.getAttribute(OpenApiRequestAttributes.RESPONSE_PLAIN_SUMMARY),
                request.getAttribute(OpenApiRequestAttributes.RESPONSE_CIPHER_SUMMARY));
    }

    /**
     * 提取请求查询参数名摘要。
     * <p>
     * OpenAPI 通常使用 POST JSON body，若商户额外传了 query 参数，只记录参数名用于定位接入差异，不记录参数值。
     * </p>
     * @param request 当前 HTTP 请求
     * @return 查询参数名列表，无参数时返回空列表
     */
    private List<String> queryKeys(HttpServletRequest request) {
        if (request.getParameterMap().isEmpty()) {
            return Collections.emptyList();
        }
        return request.getParameterMap().keySet().stream().sorted().toList();
    }

    /**
     * 从 OpenAPI 标准路径中提取 API 版本号。
     * <p>
     * 仅识别形如 v1、v2 的路径片段，用于日志维度聚合和商户接入排查；无法识别时返回 null。
     * </p>
     * @param path HTTP 请求路径
     * @return API 版本号，无法识别时返回 null
     */
    private String apiVersion(String path) {
        String[] segments = segments(path);
        for (String segment : segments) {
            if (segment != null && segment.matches("v\\d+")) {
                return segment;
            }
        }
        return null;
    }

    /**
     * 从请求路径中识别接口业务类型。
     * <p>
     * 商户 OpenAPI 使用 /api/rest/{domain}/{version} 路径时返回 domain；渠道回调路径返回 channel-callback，
     * 用于区分交易、退款、回调等日志场景。
     * </p>
     * @param path HTTP 请求路径
     * @return 接口业务类型，无法识别时返回 null
     */
    private String interfaceType(String path) {
        String[] segments = segments(path);
        for (int index = 0; index < segments.length; index++) {
            if ("rest".equals(segments[index]) && index + 1 < segments.length) {
                return segments[index + 1];
            }
        }
        if (segments.length > 0 && "channel".equals(segments[0])) {
            return "channel-callback";
        }
        return null;
    }

    /**
     * 将 HTTP 路径拆分为非前导斜杠片段。
     * <p>
     * 该结果仅用于 OpenAPI 版本和接口域识别，不参与路由决策和安全校验。
     * </p>
     * @param path HTTP 请求路径
     * @return 路径片段数组，路径为空时返回空数组
     */
    private String[] segments(String path) {
        if (!StringUtils.hasText(path)) {
            return new String[0];
        }
        return path.replaceFirst("^/+", "").split("/");
    }

    /**
     * 提取商户请求来源 IP。
     * <p>
     * 优先使用代理透传的 X-Forwarded-For 首个地址，用于 OpenAPI 接入日志和白名单排查；字段可能包含公网代理地址，
     * 不打印完整代理链。
     * </p>
     * @param request 当前 HTTP 请求
     * @return 请求来源 IP
     */
    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
