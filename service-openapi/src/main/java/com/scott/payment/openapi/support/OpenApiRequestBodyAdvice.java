package com.scott.payment.openapi.support;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Type;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestBodyAdvice
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 请求体解密与参数校验处理器，位于 service-openapi 支撑层，只负责把密文 data 解密为接口 DTO 并执行注解声明的校验分组。
 * @status : create
 */
@Slf4j
@RestControllerAdvice
public class OpenApiRequestBodyAdvice extends RequestBodyAdviceAdapter {

    /**
     * 开放 API 密文解码器，负责从请求体提取 data、解密并转换成目标 DTO。
     */
    private final OpenApiPayloadDecoder payloadDecoder;

    /**
     * 开放 API 参数校验器，负责根据注解配置的校验分组执行 Bean Validation。
     */
    private final OpenApiValidator openApiValidator;

    /**
     * 创建开放接口请求体处理器。
     *
     * @param payloadDecoder  OpenAPI 请求体密文解码器
     * @param openApiValidator OpenAPI 请求 DTO 参数校验器
     */
    public OpenApiRequestBodyAdvice(OpenApiPayloadDecoder payloadDecoder, OpenApiValidator openApiValidator) {
        this.payloadDecoder = payloadDecoder;
        this.openApiValidator = openApiValidator;
    }

    /**
     * 判断是否需要对当前请求体做开放 API 解密处理。
     *
     * @param methodParameter 控制器方法参数
     * @param targetType      目标类型
     * @param converterType   消息转换器类型
     * @return 是否支持处理
     */
    @Override
    public boolean supports(MethodParameter methodParameter, Type targetType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return methodParameter.hasParameterAnnotation(RequestBody.class)
                && String.class.equals(methodParameter.getParameterType())
                && findAnnotation(methodParameter) != null;
    }

    /**
     * 在 String 请求体读取后完成密文解密、DTO 转换和属性校验。
     *
     * @param body          原始请求体
     * @param inputMessage  HTTP 输入消息
     * @param parameter     控制器参数
     * @param targetType    目标类型
     * @param converterType 消息转换器类型
     * @return 保持原始请求体传递给控制器的 String 参数
     */
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
            openApiValidator.validate(data, annotation.validationGroups());
        }
        log.info("开放接口请求体解密完成，商户号：{}，脱敏后的请求参数：{}",
                headerDTO == null ? null : headerDTO.getMerchantId(),
                SensitiveDataMaskUtils.maskJson(JsonUtils.toJsonString(data)));
        request.setAttribute(OpenApiRequestAttributes.DECRYPTED_DATA, data);
        return body;
    }

    /**
     * 查找控制器方法上的开放接口验签与处理注解。
     *
     * @param parameter 控制器方法参数
     * @return 注解配置；不存在时返回 null
     */
    private VerificationAndProcessing findAnnotation(MethodParameter parameter) {
        if (parameter.getMethod() == null) {
            return null;
        }
        return AnnotationUtils.findAnnotation(parameter.getMethod(), VerificationAndProcessing.class);
    }

    /**
     * 获取当前线程绑定的 Servlet 请求对象。
     *
     * @return 当前 HTTP 请求
     */
    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        return attributes.getRequest();
    }
}
