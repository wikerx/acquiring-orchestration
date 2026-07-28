package com.scott.payment.component.security.jwt;

import lombok.Data;

import java.io.Serializable;


@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JwtMerchantClaims
 * @date : 2026-05-28 16:17
 * @email : scott_x@163.com
 * @description : JWT Merchant Claims 协作组件，位于 公共组件库，封装 jwt商户claims 相关的校验、转换、持久化访问或运行时协作入口。
 * @status : create
 */
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
