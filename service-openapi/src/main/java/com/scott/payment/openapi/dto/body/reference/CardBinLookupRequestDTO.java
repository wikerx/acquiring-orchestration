package com.scott.payment.openapi.dto.body.reference;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.io.Serializable;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinLookupRequestDTO
 * @date : 2026-08-11 15:44
 * @email : scott_x@163.com
 * @description : 商户 OpenAPI 卡 BIN 检索请求；至少 6 位纯数字，长输入仅取前 11 位查询且不得记录原文。
 * @status : create
 */
@Data
public class CardBinLookupRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 待检索卡 BIN，至少 6 位纯数字；超过 11 位时只使用前 11 位，日志不得输出原文。
     */
    @NotBlank(message = "cardBin is required")
    @Pattern(regexp = "^[0-9]{6,}$", message = "cardBin must contain at least 6 digits")
    private String cardBin;
}
