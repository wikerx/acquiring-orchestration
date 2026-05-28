package com.sinopay.payment.openapi.api.rest.v1.dto.body;

import lombok.Data;

import java.io.Serializable;

import javax.validation.constraints.NotBlank;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiEncryptedRequestDTO
 * @date : 2026-05-28 16:22
 * @email : scott_x@163.com
 * @description : 开放接口密文请求体
 * @status : create
 */
@Data
public class OpenApiEncryptedRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "data can not be blank")
    private String data;
}
