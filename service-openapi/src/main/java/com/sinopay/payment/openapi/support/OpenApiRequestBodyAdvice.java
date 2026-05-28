package com.sinopay.payment.openapi.support;

import com.sinopay.payment.openapi.annotation.v1.VerificationAndProcessing;
import com.sinopay.payment.openapi.api.rest.v1.dto.header.OpenApiRequestHeaderDTO;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import javax.servlet.http.HttpServletRequest;
import java.lang.reflect.Type;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestBodyAdvice
 * @date : 2026-05-28 11:25
 * @email : scott_x@163.com
 * @description : 开放接口请求体解密转换处理器
 * @status : create
 */
@Component
public class OpenApiRequestBodyAdvice extends RequestBodyAdviceAdapter {

    private final OpenApiPayloadDecoder payloadDecoder;
    private final OpenApiValidator openApiValidator;

    public OpenApiRequestBodyAdvice(OpenApiPayloadDecoder payloadDecoder, OpenApiValidator openApiValidator) {
        this.payloadDecoder = payloadDecoder;
        this.openApiValidator = openApiValidator;
    }

    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return methodParameter.hasParameterAnnotation(RequestBody.class)
                && String.class.equals(methodParameter.getParameterType())
                && findAnnotation(methodParameter) != null;
    }

    @Override
    public Object afterBodyRead(Object body, HttpInputMessage inputMessage, MethodParameter parameter,
                                Type targetType, Class<? extends HttpMessageConverter<?>> converterType) {
        VerificationAndProcessing annotation = findAnnotation(parameter);
        if (annotation == null || Void.class.equals(annotation.dataReceiver())) {
            return body;
        }
        HttpServletRequest request = currentRequest();
        OpenApiRequestHeaderDTO headerDTO = (OpenApiRequestHeaderDTO) request.getAttribute(OpenApiRequestAttributes.REQUEST_HEADER);
        Object data = payloadDecoder.decode(String.valueOf(body), annotation.dataReceiver(), headerDTO);
        if (annotation.validator()) {
            openApiValidator.validate(data);
        }
        request.setAttribute(OpenApiRequestAttributes.DECRYPTED_DATA, data);
        return body;
    }

    private VerificationAndProcessing findAnnotation(MethodParameter parameter) {
        return AnnotationUtils.findAnnotation(parameter.getMethod(), VerificationAndProcessing.class);
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attributes.getRequest();
    }
}
