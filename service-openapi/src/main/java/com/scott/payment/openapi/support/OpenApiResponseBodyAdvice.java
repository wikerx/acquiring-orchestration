package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.service.MerchantSecurityService;
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

import jakarta.servlet.http.HttpServletRequest;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiResponseBodyAdvice
 * @date : 2026-06-02 15:20
 * @email : scott_x@163.com
 * @description : OpenAPI 响应 data 强制加密处理器
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
     * 创建 OpenAPI 响应加密处理器。
     *
     * @param payloadCrypto           OpenAPI 报文混合加密工具
     * @param merchantSecurityService 商户安全材料服务
     */
    public OpenApiResponseBodyAdvice(OpenApiPayloadCrypto payloadCrypto,
                                     MerchantSecurityService merchantSecurityService) {
        this.payloadCrypto = payloadCrypto;
        this.merchantSecurityService = merchantSecurityService;
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
     * 失败响应通常没有 data，因此只保留 code/message 明文；成功响应的 data 会被平台使用商户响应公钥加密，
     * 商户侧再使用自己保存的响应私钥解密。
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
            return body;
        }
        OpenApiRequestHeaderDTO headerDTO = getHeaderContext(request);
        String merchantId = headerDTO.getMerchantId();
        String plainDataJson = JsonUtils.toJsonString(result.getData());
        String encryptedData = payloadCrypto.encrypt(
                plainDataJson,
                merchantSecurityService.getMerchantResponsePublicKey(merchantId)
        );
        log.info("开放接口响应data加密完成，商户号：{}，响应明文长度：{}，响应密文长度：{}",
                merchantId,
                plainDataJson.length(),
                encryptedData.length());

        CommonResult<Object> encryptedResult = new CommonResult<>();
        encryptedResult.setCode(result.getCode());
        encryptedResult.setMessage(result.getMessage());
        encryptedResult.setData(encryptedData);
        return encryptedResult;
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
}
