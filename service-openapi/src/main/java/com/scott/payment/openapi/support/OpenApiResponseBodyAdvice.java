package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.CommonResult;
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


@Slf4j
@RestControllerAdvice
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiResponseBodyAdvice
 * @date : 2026-06-02 11:14
 * @email : scott_x@163.com
 * @description : OpenApiResponseBodyAdvice Java 类型，用于封装当前包内的领域数据、服务契约或模块协作逻辑，位于 商户开放接口服务层，输入输出边界由所在包和公开方法契约限定。
 * @status : create
 */
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
     * 创建 OpenAPI 响应加密处理器。
     *
     * @param payloadCrypto           OpenAPI 报文混合加密工具
     * @param merchantSecurityService 商户安全材料服务
     * @param paymentInternalClient   service-payment 内部客户端
     */
    public OpenApiResponseBodyAdvice(OpenApiPayloadCrypto payloadCrypto,
                                     MerchantSecurityService merchantSecurityService,
                                     PaymentInternalClient paymentInternalClient) {
        this.payloadCrypto = payloadCrypto;
        this.merchantSecurityService = merchantSecurityService;
        this.paymentInternalClient = paymentInternalClient;
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
            log.warn("商户OpenAPI响应日志密文摘要回写失败，transactionId：{}，原因：{}",
                    transactionInfo.getTransactionId(), exception.getMessage());
        }
    }

    /**
     * 解析 resolve Transaction Info 对应的业务值，按优先级从上下文、请求或配置中取值。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 OpenApiResponseBodyAdvice 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param data data 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    private PaymentCreateVO.TransactionInfoVO resolveTransactionInfo(Object data) {
        if (data instanceof PaymentCreateVO createVO) {
            return createVO.getTransactionInfo();
        }
        return null;
    }

    /**
     * 完成 mask Cipher 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 OpenApiResponseBodyAdvice 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param encryptedData encrypted Data 输入值，含义由调用方法名称和所属业务对象限定
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
     * 完成 sha256 Hex 的本地校验、字段转换或结果组装，供当前调用链继续使用。
     * <p>
     * 层级边界：商户开放接口服务层；输入来源、输出结构和异常语义由 OpenApiResponseBodyAdvice 的方法签名及调用链约束。
     * 状态变更、事务提交、MQ 投递、远程调用和敏感数据处理以当前方法实现为准，调用方需沿用既有幂等与脱敏约束。
     * </p>
     * @param value 待校验或转换的原始值
     * @return 方法签名声明的返回值，具体结构由返回类型定义
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
}
