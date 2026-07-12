package com.scott.payment.openapi.dto.header;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestHeaderDTO
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 开放接口请求头数据传输对象
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiRequestHeaderDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIOpen Api Request Header 数据传输对象，位于 service-openapi 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class OpenApiRequestHeaderDTO implements Serializable {

    /**
     * 序列化版本号，用于保证请求头上下文对象在链路传递时的反序列化兼容性。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 原始 Authorization 请求头，默认承载商户按 HS256 生成的 JWT token。
     */
    private String authorization;

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
}
