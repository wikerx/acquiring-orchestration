package com.scott.payment.openapi.support;

import com.scott.payment.component.core.enums.ApiCoResultEnum;
import com.scott.payment.component.core.exception.ApiException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.security.crypto.OpenApiPayloadCrypto;
import com.scott.payment.openapi.dto.body.OpenApiEncryptedRequestDTO;
import com.scott.payment.openapi.dto.header.OpenApiRequestHeaderDTO;
import com.scott.payment.openapi.security.OpenApiPayloadKeyProvider;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiPayloadDecoder
 * @date : 2026-05-28 11:25
 * @email : scott_x@163.com
 * @description : 开放接口密文数据解密与转换器
 * @status : create
 */
@Component
public class OpenApiPayloadDecoder {

    /**
     * OpenAPI 报文混合加密工具，负责解析 data compact 密文并执行 RSA-OAEP/AES-GCM 解密。
     */
    private final OpenApiPayloadCrypto payloadCrypto;

    /**
     * 平台 RSA 私钥提供器，生产环境通过 merchantId 从数据库、KMS 或 HSM 获取当前商户独立私钥。
     */
    private final OpenApiPayloadKeyProvider payloadKeyProvider;

    public OpenApiPayloadDecoder(OpenApiPayloadCrypto payloadCrypto, OpenApiPayloadKeyProvider payloadKeyProvider) {
        this.payloadCrypto = payloadCrypto;
        this.payloadKeyProvider = payloadKeyProvider;
    }

    /**
     * 解密并转换商户密文请求体。
     *
     * @param requestBody  商户原始请求体
     * @param dataReceiver 解密后接收 DTO 类型
     * @param headerDTO    已通过验证的请求头信息
     * @return 解密后的 DTO 对象
     */
    public Object decode(String requestBody, Class<?> dataReceiver, OpenApiRequestHeaderDTO headerDTO) {
        if (!StringUtils.hasText(requestBody)) {
            throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_MISSING, "data");
        }
        String cipherText = extractCipherText(requestBody);
        String plainText = decrypt(cipherText, headerDTO);
        Object data = parsePlainText(plainText, dataReceiver);
        if (data == null) {
            throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_ILLEGAL);
        }
        return data;
    }

    private String extractCipherText(String requestBody) {
        String trimmedBody = requestBody.trim();
        if (!trimmedBody.startsWith("{")) {
            return trimmedBody;
        }
        OpenApiEncryptedRequestDTO encryptedRequestDTO = JsonUtils.parseObject(trimmedBody, OpenApiEncryptedRequestDTO.class);
        if (encryptedRequestDTO != null && StringUtils.hasText(encryptedRequestDTO.getData())) {
            return encryptedRequestDTO.getData();
        }
        return trimmedBody;
    }

    private Object parsePlainText(String plainText, Class<?> dataReceiver) {
        try {
            return JsonUtils.parseObject(plainText, dataReceiver);
        } catch (Exception exception) {
            throw new ApiException(ApiCoResultEnum.CO_REQUIRED_PARAMETER_ILLEGAL);
        }
    }

    /**
     * 使用平台私钥解密商户 data 密文。
     * <p>
     * 当前 headerDTO 已经由拦截器完成 JWT 验签，这里再次检查 merchantId，避免绕过拦截器直接进入请求体解析流程。
     *
     * @param encryptedData 商户提交的 compact 密文
     * @param headerDTO     已验签的请求头上下文
     * @return 解密后的业务 JSON 明文
     */
    private String decrypt(String encryptedData, OpenApiRequestHeaderDTO headerDTO) {
        if (headerDTO == null || !StringUtils.hasText(headerDTO.getMerchantId())) {
            throw new ApiException(ApiCoResultEnum.CO_UNAUTHORIZED);
        }
        return payloadCrypto.decrypt(encryptedData.trim(), payloadKeyProvider.getPlatformPrivateKey(headerDTO.getMerchantId()));
    }
}
