package com.sinopay.payment.openapi.support;

import com.sinopay.payment.component.core.constant.ErrorCode;
import com.sinopay.payment.component.core.exception.BizException;
import com.sinopay.payment.openapi.annotation.v1.VerificationAndProcessing;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import javax.servlet.http.HttpServletRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestArgumentResolver
 * @date : 2026-05-28 11:25
 * @email : scott_x@163.com
 * @description : 开放接口解密 DTO 参数解析器
 * @status : create
 */
@Component
public class OpenApiRequestArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        VerificationAndProcessing annotation = AnnotationUtils.findAnnotation(
                parameter.getMethod(),
                VerificationAndProcessing.class
        );
        return annotation != null
                && !Void.class.equals(annotation.dataReceiver())
                && annotation.dataReceiver().isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "http request can not be resolved");
        }
        Object data = request.getAttribute(OpenApiRequestAttributes.DECRYPTED_DATA);
        if (data == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "request body has not been processed");
        }
        return data;
    }
}
