package com.sinopay.payment.openapi.support;

import com.sinopay.payment.component.core.constant.ErrorCode;
import com.sinopay.payment.component.core.exception.BizException;
import com.sinopay.payment.component.core.json.JsonUtils;
import com.sinopay.payment.openapi.api.rest.v1.dto.header.OpenApiRequestHeaderDTO;
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

    public Object decode(String encryptedData, Class<?> dataReceiver, OpenApiRequestHeaderDTO headerDTO) {
        if (!StringUtils.hasText(encryptedData)) {
            throw new BizException(ErrorCode.PARAM_INVALID, "request body can not be blank");
        }
        String plainText = decrypt(encryptedData, headerDTO);
        Object data = JsonUtils.parseObject(plainText, dataReceiver);
        if (data == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "request body can not be parsed");
        }
        return data;
    }

    private String decrypt(String encryptedData, OpenApiRequestHeaderDTO headerDTO) {
        // TODO 接入商户密钥后，在这里按 appId/merchantId 做真实解密。
        return encryptedData.trim();
    }
}
