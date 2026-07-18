package com.scott.payment.component.security.jwt;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JwtMerchantClaims
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 JWT 授权声明，位于 component-security 安全组件层，只保存已验签 token 中允许在请求上下文传递的非敏感声明。
 * @status : create
 */
@Data
public class JwtMerchantClaims implements Serializable {

    /**
     * 序列化版本号，用于保证 JWT 声明对象在请求上下文传递时的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 商户号，来自 JWT Payload 的 merchantId 字段，用于定位商户配置和密钥。
     */
    private String merchantId;

    /**
     * JWT 唯一标识，来自 Payload 的 jti 字段，可用于防重放和请求审计。
     */
    private String jwtId;

    /**
     * JWT 签发时间，秒级时间戳，来自 Payload 的 iat 字段。
     */
    private long issuedAt;

    /**
     * JWT 过期时间，秒级时间戳，来自 Payload 的 exp 字段。
     */
    private long expiresAt;
}
