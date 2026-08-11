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
 * @description : 商户 OpenAPI 卡 BIN 检索明文请求，严格限制为 6 至 11 位数字以阻止完整卡号进入接口
 * @status : create
 */
@Data
public class CardBinLookupRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 待检索卡 BIN，格式为 6 至 11 位纯数字，不允许为空；日志不得完整输出。
     */
    @NotBlank(message = "cardBin is required")
    @Pattern(regexp = "^[0-9]{6,11}$", message = "cardBin must be 6 to 11 digits")
    private String cardBin;
}
