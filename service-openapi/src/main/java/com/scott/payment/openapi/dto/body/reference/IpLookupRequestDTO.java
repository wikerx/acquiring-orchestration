package com.scott.payment.openapi.dto.body.reference;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLookupRequestDTO
 * @date : 2026-08-11 15:44
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI IP 归属检索明文请求，解密后只接受单个 IPv4 或 IPv6 字面量
 * @status : create
 */
@Data
public class IpLookupRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 待检索 IP，最大 45 个字符，不允许为空；属于可识别数据，日志不得完整输出。
     */
    @NotBlank(message = "ipAddress is required")
    @Size(max = 45, message = "ipAddress length must be less than or equal to 45")
    private String ipAddress;
}
