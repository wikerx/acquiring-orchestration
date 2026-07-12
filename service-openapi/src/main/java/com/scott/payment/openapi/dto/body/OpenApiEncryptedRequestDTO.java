package com.scott.payment.openapi.dto.body;

import com.scott.payment.openapi.annotation.VerificationAndProcessing;
import lombok.Data;

import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiEncryptedRequestDTO
 * @date : 2026-05-28 16:22
 * @email : scott_x@163.com
 * @description : 开放接口密文请求体
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiEncryptedRequestDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Encrypted Request 数据传输对象，位于 service-openapi 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class OpenApiEncryptedRequestDTO implements Serializable {

    /**
     * 序列化版本号，用于保证开放接口请求体在切面、参数解析器之间传递时的兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 商户提交的加密业务报文，使用 protectedHeader.encryptedKey.iv.cipherText.tag 五段式 compact 格式。
     * <p>
     * 解密后会转换成 {@link VerificationAndProcessing#dataReceiver()} 指定的 DTO。
     */
    @NotBlank(message = "data can not be blank")
    private String data;
}
