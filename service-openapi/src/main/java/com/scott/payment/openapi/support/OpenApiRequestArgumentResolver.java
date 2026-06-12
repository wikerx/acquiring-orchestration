package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import jakarta.servlet.http.HttpServletRequest;

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

    /**
     * 开放 API 密文解码器，用于处理请求参数中的 data 密文。
     */
    private final OpenApiPayloadDecoder payloadDecoder;

    /**
     * 开放 API 参数校验器，负责校验请求参数解密后的 DTO。
     */
    private final OpenApiValidator openApiValidator;

    /**
     * 创建开放接口解密 DTO 参数解析器。
     *
     * @param payloadDecoder  开放 API 密文解码器
     * @param openApiValidator 开放 API 参数校验器
     */
    public OpenApiRequestArgumentResolver(OpenApiPayloadDecoder payloadDecoder, OpenApiValidator openApiValidator) {
        this.payloadDecoder = payloadDecoder;
        this.openApiValidator = openApiValidator;
    }

    /**
     * 判断当前参数是否为注解声明的解密 DTO。
     *
     * @param parameter 控制器方法参数
     * @return 是否支持解析
     */
    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        if (parameter.getMethod() == null) {
            return false;
        }
        VerificationAndProcessing annotation = AnnotationUtils.findAnnotation(
                parameter.getMethod(),
                VerificationAndProcessing.class
        );
        return annotation != null
                && !Void.class.equals(annotation.dataReceiver())
                && annotation.dataReceiver().isAssignableFrom(parameter.getParameterType());
    }

    /**
     * 从请求上下文中取出解密后的 DTO 并注入控制器参数。
     *
     * @param parameter     控制器方法参数
     * @param mavContainer  MVC 容器
     * @param webRequest    Web 请求
     * @param binderFactory 参数绑定工厂
     * @return 解密后的 DTO 对象
     */
    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request == null) {
            throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "http request can not be resolved");
        }
        Object data = request.getAttribute(OpenApiRequestAttributes.DECRYPTED_DATA);
        if (data == null) {
            data = resolveQueryData(parameter, request);
        }
        return data;
    }

    private Object resolveQueryData(MethodParameter parameter, HttpServletRequest request) {
        VerificationAndProcessing annotation = AnnotationUtils.findAnnotation(
                parameter.getMethod(),
                VerificationAndProcessing.class
        );
        if (annotation == null || Void.class.equals(annotation.dataReceiver())) {
            throw new ApiException(ApiResultEnum.ENCRYPTED_DATA_INVALID);
        }
        String encryptedData = request.getParameter("data");
        if (!StringUtils.hasText(encryptedData)) {
            throw new ApiException(ApiResultEnum.PARAM_MISSING, "data");
        }
        OpenApiRequestHeaderDTO headerDTO = (OpenApiRequestHeaderDTO) request.getAttribute(
                OpenApiRequestAttributes.REQUEST_HEADER
        );
        Object data = payloadDecoder.decode(encryptedData, annotation.dataReceiver(), headerDTO);
        if (annotation.validator()) {
            openApiValidator.validate(data, annotation.validationGroups());
        }
        request.setAttribute(OpenApiRequestAttributes.DECRYPTED_DATA, data);
        return data;
    }
}
