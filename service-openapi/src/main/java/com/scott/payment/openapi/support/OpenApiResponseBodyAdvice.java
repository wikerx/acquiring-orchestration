package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.core.util.SensitiveDataMaskUtils;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.client.payment.PaymentInternalClient;
import com.scott.payment.openapi.client.payment.dto.TransactionMerchantApiResponseLogUpdateClientRequestDTO;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.service.MerchantSecurityService;
import com.scott.payment.openapi.vo.payment.PaymentCreateVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.util.StringUtils;

import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiResponseBodyAdvice
 * @date : 2026-06-02 11:14
 * @email : scott_x@163.com
 * @description : Open API Response Body Advice MVC 扩展组件，位于 商户开放接口服务，在请求体读取或响应写出阶段执行解密、加密、校验、摘要记录和上下文回填。
 * @status : create
 */
@Slf4j
@RestControllerAdvice
public class OpenApiResponseBodyAdvice implements ResponseBodyAdvice<Object> {

    /**
     * OpenAPI 报文混合加密工具，用于把服务端响应 data 加密成商户可解密的 compact 密文。
     */
    private final OpenApiPayloadCrypto payloadCrypto;

    /**
     * 商户安全材料服务，用于根据 merchantId 查询商户响应公钥。
     */
    private final MerchantSecurityService merchantSecurityService;

    /**
     * service-payment 内部客户端，用于回写商户响应加密后的日志摘要。
     */
    private final PaymentInternalClient paymentInternalClient;

    /**
     * OpenAPI 诊断日志支撑组件，用于生成响应明文和密文摘要。
     */
    private final OpenApiDiagnosticLogSupport diagnosticLogSupport;

    /**
     * 创建 OpenAPI 响应加密处理器。
     *
     * @param payloadCrypto           OpenAPI 报文混合加密工具
     * @param merchantSecurityService 商户安全材料服务
     * @param paymentInternalClient   service-payment 内部客户端
     * @param diagnosticLogSupport    OpenAPI 诊断日志摘要组件
     */
    public OpenApiResponseBodyAdvice(OpenApiPayloadCrypto payloadCrypto,
                                     MerchantSecurityService merchantSecurityService,
                                     PaymentInternalClient paymentInternalClient,
                                     OpenApiDiagnosticLogSupport diagnosticLogSupport) {
        this.payloadCrypto = payloadCrypto;
        this.merchantSecurityService = merchantSecurityService;
        this.paymentInternalClient = paymentInternalClient;
        this.diagnosticLogSupport = diagnosticLogSupport;
    }

    /**
     * 判断当前控制器方法是否需要执行响应 data 加密。
     *
     * @param returnType    控制器返回值类型
     * @param converterType HTTP 消息转换器类型
     * @return true 表示当前方法带有开放接口处理注解，需要响应加密
     */
    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return returnType.getMethod() != null
                && AnnotationUtils.findAnnotation(returnType.getMethod(), VerificationAndProcessing.class) != null;
    }

    /**
     * 在响应写出前强制加密 CommonResult.data。
     * <p>
     * 失败响应通常没有 data，因此只保留 code/message 明文；成功响应 data 使用商户响应公钥加密。
     * 交易类响应会额外回写密文掩码和摘要，后台可核验商户最终收到的响应数据。
     *
     * @param body                  控制器返回对象
     * @param returnType            控制器返回值类型
     * @param selectedContentType   HTTP 响应内容类型
     * @param selectedConverterType HTTP 消息转换器类型
     * @param request               HTTP 请求
     * @param response              HTTP 响应
     * @return 加密后的响应对象
     */
    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (!(body instanceof CommonResult<?> result) || result.getData() == null) {
            if (body instanceof CommonResult<?> commonResult && request instanceof ServletServerHttpRequest servletRequest) {
                HttpServletRequest httpRequest = servletRequest.getServletRequest();
                httpRequest.setAttribute(OpenApiRequestAttributes.BUSINESS_CODE, commonResult.getCode());
                String responseSummary = diagnosticLogSupport.responseEnvelopeSummary(commonResult);
                httpRequest.setAttribute(OpenApiRequestAttributes.RESPONSE_PLAIN_SUMMARY, responseSummary);
                log.info("event: OPENAPI_RESPONSE_PLAIN_END stage=RESPONSE traceId: {} merchantId: {} path: {} platformCode: {} encryptRequired=false responseSummary: {}",
                        TraceContext.getTraceId(),
                        merchantIdSafely(httpRequest),
                        request.getURI().getPath(),
                        commonResult.getCode(),
                        responseSummary);
            }
            return body;
        }
        OpenApiRequestHeaderDTO headerDTO = getHeaderContext(request);
        String merchantId = headerDTO.getMerchantId();
        HttpServletRequest httpServletRequest = servletRequest(request);
        if (request instanceof ServletServerHttpRequest servletRequest) {
            servletRequest.getServletRequest().setAttribute(OpenApiRequestAttributes.BUSINESS_CODE, result.getCode());
        }
        String plainDataJson = JsonUtils.toJsonString(result.getData());
        String encryptedData = payloadCrypto.encrypt(
                plainDataJson,
                merchantSecurityService.getMerchantResponsePublicKey(merchantId)
        );
        String plainSummary = diagnosticLogSupport.plainResponseSummary(result.getData());
        String cipherSummary = diagnosticLogSupport.cipherResponseSummary(encryptedData);
        httpServletRequest.setAttribute(OpenApiRequestAttributes.RESPONSE_PLAIN_SUMMARY, plainSummary);
        httpServletRequest.setAttribute(OpenApiRequestAttributes.RESPONSE_CIPHER_SUMMARY, cipherSummary);
        log.info("event: OPENAPI_RESPONSE_ENCRYPT_END stage=ENCRYPT traceId: {} merchantId: {} path: {} platformCode: {} encryptSuccess=true plainLength: {} cipherLength: {} plainResponseSummary: {} cipherResponseSummary: {}",
                TraceContext.getTraceId(),
                merchantId,
                request.getURI().getPath(),
                result.getCode(),
                plainDataJson.length(),
                encryptedData.length(),
                plainSummary,
                cipherSummary);
        updateMerchantApiResponseLog(result.getData(), plainDataJson, encryptedData);

        CommonResult<Object> encryptedResult = new CommonResult<>();
        encryptedResult.setCode(result.getCode());
        encryptedResult.setMessage(result.getMessage());
        encryptedResult.setData(encryptedData);
        return encryptedResult;
    }

    /**
     * 回写商户交易响应日志的密文摘要。
     * <p>
     * 该操作属于审计增强，失败不能影响商户交易响应；日志只记录交易 ID 和失败摘要，不输出完整响应明文或密文。
     *
     * @param data          加密前响应 data
     * @param plainDataJson 加密前明文 JSON
     * @param encryptedData 加密后 compact 密文
     */
    private void updateMerchantApiResponseLog(Object data, String plainDataJson, String encryptedData) {
        PaymentCreateVO.TransactionInfoVO transactionInfo = resolveTransactionInfo(data);
        if (transactionInfo == null || !StringUtils.hasText(transactionInfo.getTransactionId())) {
            return;
        }
        TransactionMerchantApiResponseLogUpdateClientRequestDTO requestDTO =
                new TransactionMerchantApiResponseLogUpdateClientRequestDTO();
        requestDTO.setTransactionId(transactionInfo.getTransactionId());
        requestDTO.setResponsePlainJsonMasked(SensitiveDataMaskUtils.maskJsonSafely(plainDataJson));
        requestDTO.setResponseCipherDigest(sha256Hex(encryptedData));
        requestDTO.setResponseCipherMasked(maskCipher(encryptedData));
        requestDTO.setResponseTime(LocalDateTime.now());
        if (data instanceof PaymentCreateVO createVO && createVO.getOrderInfo() != null) {
            requestDTO.setRequestId(createVO.getOrderInfo().getOrderId());
        }
        try {
            paymentInternalClient.updateMerchantApiResponseLog(requestDTO);
        } catch (RuntimeException exception) {
            log.warn("event: OPENAPI_RESPONSE_LOG_UPDATE_FAILED stage=RESPONSE_LOG traceId: {} transactionId: {} exceptionType: {} reason: {}",
                    TraceContext.getTraceId(),
                    transactionInfo.getTransactionId(),
                    exception.getClass().getSimpleName(),
                    exception.getMessage());
        }
    }

    /**
     * 从 OpenAPI 响应对象中解析平台交易信息。
     *
     * @param data 响应 data 对象，当前仅支付创建响应携带交易信息
     * @return 交易信息对象；非支付创建响应或缺失交易信息时返回 null
     */
    private PaymentCreateVO.TransactionInfoVO resolveTransactionInfo(Object data) {
        if (data instanceof PaymentCreateVO createVO) {
            return createVO.getTransactionInfo();
        }
        return null;
    }

    /**
     * 对 OpenAPI 响应密文做首尾掩码。
     * <p>
     * 该值用于后台交易详情和日志比对商户实际收到的 data，不输出完整密文，也不参与解密或签名校验。
     * </p>
     * @param encryptedData 响应 data compact 密文
     * @return 可安全记录的密文掩码；密文为空时返回 null
     */
    private String maskCipher(String encryptedData) {
        if (!StringUtils.hasText(encryptedData)) {
            return null;
        }
        String normalized = encryptedData.trim();
        if (normalized.length() <= 16) {
            return "***";
        }
        return normalized.substring(0, 8) + "***" + normalized.substring(normalized.length() - 8);
    }

    /**
     * 计算响应密文的 SHA-256 十六进制摘要。
     * <p>
     * 摘要用于关联 response advice、内部回写接口和商户排障反馈，不保存完整密文或响应明文。
     * </p>
     * @param value 待摘要文本
     * @return SHA-256 十六进制摘要
     */
    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new ApiException(ApiResultEnum.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 从请求上下文获取已经验签通过的商户请求头信息。
     *
     * @param request HTTP 请求
     * @return 请求头上下文
     */
    private OpenApiRequestHeaderDTO getHeaderContext(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED);
        }
        HttpServletRequest httpServletRequest = servletRequest.getServletRequest();
        Object value = httpServletRequest.getAttribute(OpenApiRequestAttributes.REQUEST_HEADER);
        if (!(value instanceof OpenApiRequestHeaderDTO headerDTO)) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED);
        }
        return headerDTO;
    }

    /**
     * 获取 Servlet 请求对象。
     *
     * @param request 当前响应处理请求
     * @return Servlet 请求
     */
    private HttpServletRequest servletRequest(ServerHttpRequest request) {
        if (!(request instanceof ServletServerHttpRequest servletRequest)) {
            throw new ApiException(ApiResultEnum.UNAUTHORIZED);
        }
        return servletRequest.getServletRequest();
    }

    /**
     * 安全读取商户号用于失败响应日志。
     * <p>
     * 失败响应可能发生在认证前，此时没有请求头上下文；方法只返回已验证上下文中的 merchantId。
     * </p>
     * @param request 当前 HTTP 请求
     * @return 商户号或 null
     */
    private String merchantIdSafely(HttpServletRequest request) {
        Object value = request == null ? null : request.getAttribute(OpenApiRequestAttributes.REQUEST_HEADER);
        if (value instanceof OpenApiRequestHeaderDTO headerDTO) {
            return headerDTO.getMerchantId();
        }
        return null;
    }
}
