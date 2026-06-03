package com.scott.payment.openapi.dto.body.iso;

import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IsoCurrencyQueryRequestDTO
 * @date : 2026-06-03 15:06
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 查询币种请求参数
 * @status : create
 */
@Data
public class IsoCurrencyQueryRequestDTO implements Serializable {

    /**
     * 序列化版本号，用于保证请求 DTO 在测试和日志序列化时兼容。
     */
    private static final long serialVersionUID = 1L;

    /**
     * 币种查询关键字。
     * <p>
     * 支持 ISO 4217 三位字母代码、三位数字代码、英文名、中文名和币种符号。为空时查询全部币种。
     */
    private String keyword;
}
