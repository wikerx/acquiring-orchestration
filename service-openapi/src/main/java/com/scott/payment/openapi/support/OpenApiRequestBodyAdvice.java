package com.scott.payment.openapi.support;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.security.SecurityInterceptEventRecorder;
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


@Slf4j
@RestControllerAdvice
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestBodyAdvice
 * @date : 2026-05-28 16:17
 * @email : scott_x@163.com
 * @description : OpenApiRequestBodyAdvice Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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
     * 安全拦截事件记录器，仅记录脱敏排查元数据。
     */
    private final SecurityInterceptEventRecorder securityInterceptEventRecorder;

    /**
     * 创建开放接口请求体处理器。
     *
     * @param payloadDecoder  OpenAPI 请求体密文解码器
     * @param openApiValidator OpenAPI 请求 DTO 参数校验器
     * @param securityInterceptEventRecorder 安全拦截事件记录器
     */
    public OpenApiRequestBodyAdvice(OpenApiPayloadDecoder payloadDecoder,
                                    OpenApiValidator openApiValidator,
                                    SecurityInterceptEventRecorder securityInterceptEventRecorder) {
        this.payloadDecoder = payloadDecoder;
        this.openApiValidator = openApiValidator;
        this.securityInterceptEventRecorder = securityInterceptEventRecorder;
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
        Object data;
        try {
            data = payloadDecoder.decode(String.valueOf(body), annotation.dataReceiver(), headerDTO);
        } catch (RuntimeException exception) {
            recordBlocked(request, headerDTO, "OPENAPI_DECRYPT_FAILED", SecurityInterceptEventRecorder.RISK_HIGH,
                    "OPENAPI_PAYLOAD_DECRYPT", exception);
            throw exception;
        }
        if (annotation.validator()) {
            try {
                openApiValidator.validate(data, annotation.validationGroups());
            } catch (RuntimeException exception) {
                recordBlocked(request, headerDTO, "OPENAPI_PARAM_INVALID", SecurityInterceptEventRecorder.RISK_MEDIUM,
                        "OPENAPI_PARAM_VALIDATION", exception);
                throw exception;
            }
        }
        String maskedSummary = SensitiveDataMaskUtils.maskJsonSafely(JsonUtils.toJsonString(data));
        log.info("event=OPENAPI_REQUEST_DECRYPT_END stage=DECRYPT merchantId: {} path: {} apiVersion: {} interfaceType: {} decryptSuccess=true requestSummary: {}",
                headerDTO == null ? null : headerDTO.getMerchantId(),
                request.getRequestURI(),
                request.getAttribute(OpenApiRequestAttributes.API_VERSION),
                request.getAttribute(OpenApiRequestAttributes.INTERFACE_TYPE),
                maskedSummary);
        request.setAttribute(OpenApiRequestAttributes.DECRYPTED_DATA, data);
        return body;
    }

    private void recordBlocked(HttpServletRequest request,
                               OpenApiRequestHeaderDTO headerDTO,
                               String eventType,
                               String riskLevel,
                               String hitRuleCode,
                               RuntimeException exception) {
        securityInterceptEventRecorder.recordBlocked(
                request,
                SecurityInterceptEventRecorder.SOURCE_OPENAPI,
                eventType,
                riskLevel,
                headerDTO == null ? null : headerDTO.getMerchantId(),
                hitRuleCode,
                securityInterceptEventRecorder.reasonCode(exception),
                securityInterceptEventRecorder.reasonMessage(exception)
        );
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
