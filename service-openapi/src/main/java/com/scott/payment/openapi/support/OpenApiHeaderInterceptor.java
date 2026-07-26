package com.scott.payment.openapi.support;

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


@Component
@Slf4j
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiHeaderInterceptor
 * @date : 2026-05-28 16:17
 * @email : scott_x@163.com
 * @description : OpenApiHeaderInterceptor 请求拦截组件，用于处理鉴权、链路追踪、上下文绑定和安全边界，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
public class OpenApiHeaderInterceptor implements HandlerInterceptor {

    /**
     * 请求头提取器，负责校验必填请求头并完成商户 JWT 验签。
     */
    private final OpenApiRequestHeaderExtractor headerExtractor;

    /**
     * 创建开放接口请求头拦截器。
     *
     * @param headerExtractor 请求头提取器
     */
    public OpenApiHeaderInterceptor(OpenApiRequestHeaderExtractor headerExtractor) {
        this.headerExtractor = headerExtractor;
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
        log.info("event: OPENAPI_REQUEST_ENTER stage=ACCEPT method: {} path: {} apiVersion: {} interfaceType: {} clientIp: {}",
                request.getMethod(),
                request.getRequestURI(),
                request.getAttribute(OpenApiRequestAttributes.API_VERSION),
                request.getAttribute(OpenApiRequestAttributes.INTERFACE_TYPE),
                clientIp(request));
        OpenApiRequestHeaderDTO headerDTO = headerExtractor.extract(request, annotation.requiredHeaders());
        request.setAttribute(OpenApiRequestAttributes.REQUEST_HEADER, headerDTO);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception exception) {
        Object startValue = request.getAttribute(OpenApiRequestAttributes.REQUEST_START_NANOS);
        if (!(startValue instanceof Long startNanos)) {
            return;
        }
        OpenApiRequestHeaderDTO headerDTO = (OpenApiRequestHeaderDTO) request.getAttribute(OpenApiRequestAttributes.REQUEST_HEADER);
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        log.info("event: OPENAPI_REQUEST_END stage=FINISH merchantId: {} path: {} apiVersion: {} interfaceType: {} httpStatus: {} platformCode: {} durationMs: {} exceptionType: {}",
                headerDTO == null ? null : headerDTO.getMerchantId(),
                request.getRequestURI(),
                request.getAttribute(OpenApiRequestAttributes.API_VERSION),
                request.getAttribute(OpenApiRequestAttributes.INTERFACE_TYPE),
                response.getStatus(),
                request.getAttribute(OpenApiRequestAttributes.BUSINESS_CODE),
                durationMs,
                exception == null ? null : exception.getClass().getSimpleName());
    }

    private String apiVersion(String path) {
        String[] segments = segments(path);
        for (String segment : segments) {
            if (segment != null && segment.matches("v\\d+")) {
                return segment;
            }
        }
        return null;
    }

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

    private String[] segments(String path) {
        if (!StringUtils.hasText(path)) {
            return new String[0];
        }
        return path.replaceFirst("^/+", "").split("/");
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
