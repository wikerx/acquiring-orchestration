package com.scott.payment.openapi.dto.header;

import lombok.Data;

import java.io.Serializable;


/**
 * 统一安全链路验证后的商户请求头上下文，业务层只能读取该可信对象。
 */
@Data
public class OpenApiRequestHeaderDTO implements Serializable {

    /**
     * 序列化版本号，用于保证请求头上下文对象在链路传递时的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * JWT Payload 中的 merchantId，代表支付平台为商户分配的唯一商户号。
     */
    private String merchantId;

    /**
     * JWT Payload 中的 jti，请求唯一标识，后续可配合 Redis 集群做防重放校验。
     */
    private String jwtId;

    /**
     * JWT Payload 中的 iat，秒级签发时间戳，用于校验请求是否在允许时间窗口内。
     */
    private Long issuedAt;

    /**
     * JWT Payload 中的 exp，秒级过期时间戳，最大允许与签发时间相差 3 分钟。
     */
    private Long expiresAt;

    /**
     * 网关规范化后透传的客户端 IP，仅用于审计和访问控制，不信任商户自传的转发头。
     */
    private String clientIp;
}
