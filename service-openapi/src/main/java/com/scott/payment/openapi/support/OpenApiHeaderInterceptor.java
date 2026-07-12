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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiHeaderInterceptor
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Header Interceptor，位于 service-openapi 的支撑组件层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
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
    /**
     * 执行商户 OpenAPI相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param response 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param handler 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
        OpenApiRequestHeaderDTO headerDTO = headerExtractor.extract(request, annotation.requiredHeaders());
        request.setAttribute(OpenApiRequestAttributes.REQUEST_HEADER, headerDTO);
        return true;
    }
}
