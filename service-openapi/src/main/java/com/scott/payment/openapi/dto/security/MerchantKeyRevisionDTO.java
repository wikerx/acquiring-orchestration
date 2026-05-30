package com.scott.payment.openapi.dto.security;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantKeyRevisionDTO
 * @date : 2026-05-30 09:20
 * @email : scott_x@163.com
 * @description : 商户密钥迭代记录查询结果
 * @status : create
 */
@Data
@NoArgsConstructor
public class MerchantKeyRevisionDTO {

    /**
     * 支付框架颁发的商户号。
     */
    private String merchantId;

    /**
     * 密钥类型，例如 JWT_HS256 或 RESPONSE_RSA。
     */
    private String keyType;

    /**
     * 密钥编号。JWT 密钥使用 keyVersion，响应加密公钥使用 responseKeyId。
     */
    private String keyId;

    /**
     * 密钥算法。
     */
    private String algorithm;

    /**
     * 密钥安全指纹，只用于日志和排查，不参与真实验签。
     */
    private String keyFingerprint;

    /**
     * 密钥是否启用。
     */
    private Boolean enabled;

    /**
     * 密钥生效时间。
     */
    private LocalDateTime effectiveTime;

    /**
     * 密钥失效时间，允许为空。
     */
    private LocalDateTime expireTime;
}
