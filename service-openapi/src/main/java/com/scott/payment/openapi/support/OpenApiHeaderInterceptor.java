package com.scott.payment.openapi.support;

import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiHeaderInterceptor
 * @date : 2026-05-28 11:25
 * @email : scott_x@163.com
 * @description : 开放接口请求头拦截器
 * @status : create
 */
@Component
public class OpenApiHeaderInterceptor implements HandlerInterceptor {

    /**
     * 请求头提取器，负责校验必填请求头并完成商户 JWT 验签。
     */
    private final OpenApiRequestHeaderExtractor headerExtractor;

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
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        HandlerMethod handlerMethod = (HandlerMethod) handler;
        VerificationAndProcessing annotation = AnnotationUtils.findAnnotation(
                handlerMethod.getMethod(),
                VerificationAndProcessing.class
        );
        if (annotation == null || !annotation.requiredHeader()) {
            return true;
        }
        OpenApiRequestHeaderDTO headerDTO = headerExtractor.extract(request, annotation.requiredHeaders());
        request.setAttribute(OpenApiRequestAttributes.REQUEST_HEADER, headerDTO);
        return true;
    }
}
