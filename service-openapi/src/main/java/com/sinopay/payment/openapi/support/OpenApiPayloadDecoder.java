package com.sinopay.payment.openapi.support;

import com.sinopay.payment.component.core.constant.ErrorCode;
import com.sinopay.payment.component.core.exception.BizException;
import com.sinopay.payment.component.core.json.JsonUtils;
import com.sinopay.payment.openapi.api.rest.v1.dto.body.OpenApiEncryptedRequestDTO;
import com.sinopay.payment.openapi.api.rest.v1.dto.header.OpenApiRequestHeaderDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

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

    public Object decode(String requestBody, Class<?> dataReceiver, OpenApiRequestHeaderDTO headerDTO) {
        if (!StringUtils.hasText(requestBody)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "request body can not be blank");
        }
        String cipherText = extractCipherText(requestBody);
        String plainText = decrypt(cipherText, headerDTO);
        Object data = parsePlainText(plainText, dataReceiver);
        if (data == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "request body can not be parsed");
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
            throw new BizException(ErrorCode.PARAM_INVALID, "request body json format invalid");
        }
    }

    private String decrypt(String encryptedData, OpenApiRequestHeaderDTO headerDTO) {
        // TODO 接入商户密钥后，在这里按 merchantId 做真实验真与解密。
        String value = encryptedData.trim();
        if (value.startsWith("{")) {
            return value;
        }
        String decoded = tryBase64Decode(value);
        if (decoded != null && decoded.trim().startsWith("{")) {
            return decoded.trim();
        }
        return value;
    }

    private String tryBase64Decode(String value) {
        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            try {
                return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }
    }
}
