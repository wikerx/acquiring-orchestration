package com.sinopay.payment.openapi.support;

import com.sinopay.payment.openapi.annotation.v1.VerificationAndProcessing;
import com.sinopay.payment.openapi.api.rest.v1.dto.header.OpenApiRequestHeaderDTO;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

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

    private final OpenApiRequestHeaderExtractor headerExtractor;

    public OpenApiHeaderInterceptor(OpenApiRequestHeaderExtractor headerExtractor) {
        this.headerExtractor = headerExtractor;
    }

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
