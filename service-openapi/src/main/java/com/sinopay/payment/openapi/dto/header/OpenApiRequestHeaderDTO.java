package com.sinopay.payment.openapi.dto.header;

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
@Data
public class OpenApiRequestHeaderDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String authorization;
    private String merchantId;
    private String jwtId;
    private Long issuedAt;
    private Long expiresAt;
}
