package com.scott.payment.openapi.support;

import com.scott.payment.component.core.trace.TraceContext;
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


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestBodyAdvice
 * @date : 2026-05-28 16:17
 * @email : scott_x@163.com
 * @description : Open API Request Body Advice MVC 扩展组件，位于 商户开放接口服务，在请求体读取或响应写出阶段执行解密、加密、校验、摘要记录和上下文回填。
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
     * 安全拦截事件记录器，仅记录脱敏排查元数据。
     */
    private final SecurityInterceptEventRecorder securityInterceptEventRecorder;

    /**
     * OpenAPI 诊断日志支撑组件，用于统一生成密文和明文参数摘要。
     */
    private final OpenApiDiagnosticLogSupport diagnosticLogSupport;

    /**
     * 创建开放接口请求体处理器。
     *
     * @param payloadDecoder  OpenAPI 请求体密文解码器
     * @param openApiValidator OpenAPI 请求 DTO 参数校验器
     * @param securityInterceptEventRecorder 安全拦截事件记录器
     */
    public OpenApiRequestBodyAdvice(OpenApiPayloadDecoder payloadDecoder,
                                    OpenApiValidator openApiValidator,
                                    SecurityInterceptEventRecorder securityInterceptEventRecorder,
                                    OpenApiDiagnosticLogSupport diagnosticLogSupport) {
        this.payloadDecoder = payloadDecoder;
        this.openApiValidator = openApiValidator;
        this.securityInterceptEventRecorder = securityInterceptEventRecorder;
        this.diagnosticLogSupport = diagnosticLogSupport;
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
        String requestBody = String.valueOf(body);
        String cipherSummary = diagnosticLogSupport.cipherRequestSummary(requestBody);
        request.setAttribute(OpenApiRequestAttributes.REQUEST_CIPHER_SUMMARY, cipherSummary);
        log.info("event: OPENAPI_REQUEST_CIPHER_RECEIVED stage=DECRYPT traceId: {} merchantId: {} path: {} apiVersion: {} interfaceType: {} cipherSummary: {}",
                TraceContext.getTraceId(),
                headerDTO == null ? null : headerDTO.getMerchantId(),
                request.getRequestURI(),
                request.getAttribute(OpenApiRequestAttributes.API_VERSION),
                request.getAttribute(OpenApiRequestAttributes.INTERFACE_TYPE),
                cipherSummary);
        Object data;
        try {
            data = payloadDecoder.decode(requestBody, annotation.dataReceiver(), headerDTO);
        } catch (RuntimeException exception) {
            recordBlocked(request, headerDTO, "OPENAPI_DECRYPT_FAILED", SecurityInterceptEventRecorder.RISK_HIGH,
                    "OPENAPI_PAYLOAD_DECRYPT", exception);
            request.setAttribute(OpenApiRequestAttributes.EXCEPTION_TYPE, exception.getClass().getSimpleName());
            log.warn("event: OPENAPI_REQUEST_DECRYPT_FAILED stage=DECRYPT traceId: {} merchantId: {} path: {} apiVersion: {} interfaceType: {} reasonCode: {} exceptionType: {} cipherSummary: {}",
                    TraceContext.getTraceId(),
                    headerDTO == null ? null : headerDTO.getMerchantId(),
                    request.getRequestURI(),
                    request.getAttribute(OpenApiRequestAttributes.API_VERSION),
                    request.getAttribute(OpenApiRequestAttributes.INTERFACE_TYPE),
                    securityInterceptEventRecorder.reasonCode(exception),
                    exception.getClass().getSimpleName(),
                    cipherSummary);
            throw exception;
        }
        String maskedSummary = diagnosticLogSupport.plainRequestSummary(data);
        request.setAttribute(OpenApiRequestAttributes.REQUEST_PLAIN_SUMMARY, maskedSummary);
        if (annotation.validator()) {
            try {
                openApiValidator.validate(data, annotation.validationGroups());
            } catch (RuntimeException exception) {
                recordBlocked(request, headerDTO, "OPENAPI_PARAM_INVALID", SecurityInterceptEventRecorder.RISK_MEDIUM,
                        "OPENAPI_PARAM_VALIDATION", exception);
                request.setAttribute(OpenApiRequestAttributes.EXCEPTION_TYPE, exception.getClass().getSimpleName());
                log.warn("event: OPENAPI_REQUEST_VALIDATE_FAILED stage=VALIDATE traceId: {} merchantId: {} path: {} apiVersion: {} interfaceType: {} reasonCode: {} exceptionType: {} plainRequestSummary: {}",
                        TraceContext.getTraceId(),
                        headerDTO == null ? null : headerDTO.getMerchantId(),
                        request.getRequestURI(),
                        request.getAttribute(OpenApiRequestAttributes.API_VERSION),
                        request.getAttribute(OpenApiRequestAttributes.INTERFACE_TYPE),
                        securityInterceptEventRecorder.reasonCode(exception),
                        exception.getClass().getSimpleName(),
                        maskedSummary);
                throw exception;
            }
        }
        log.info("event: OPENAPI_REQUEST_DECRYPT_END stage=DECRYPT traceId: {} merchantId: {} path: {} apiVersion: {} interfaceType: {} decryptSuccess=true cipherSummary: {} plainRequestSummary: {}",
                TraceContext.getTraceId(),
                headerDTO == null ? null : headerDTO.getMerchantId(),
                request.getRequestURI(),
                request.getAttribute(OpenApiRequestAttributes.API_VERSION),
                request.getAttribute(OpenApiRequestAttributes.INTERFACE_TYPE),
                cipherSummary,
                maskedSummary);
        request.setAttribute(OpenApiRequestAttributes.DECRYPTED_DATA, data);
        return body;
    }

    /**
     * 记录请求解密或参数校验阶段的安全拦截事件。
     *
     * <p>仅传递商户号、规则码和经统一归一化的异常原因，不记录解密后请求体、JWT、
     * 密钥、卡号、CVV 或其他认证材料。</p>
     *
     * @param request     当前 HTTP 请求
     * @param headerDTO   已校验的 OpenAPI 请求头；头解析失败时可为空
     * @param eventType   安全事件类型
     * @param riskLevel   风险等级
     * @param hitRuleCode 命中的安全规则编码
     * @param exception   触发拦截的业务异常
     */
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
