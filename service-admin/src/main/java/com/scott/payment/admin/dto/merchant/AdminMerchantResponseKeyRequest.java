package com.scott.payment.admin.dto.merchant;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 商户响应公钥维护请求。
 */
@Data
public class AdminMerchantResponseKeyRequest {

    @NotBlank(message = "响应公钥不能为空")
    private String publicKeyX509Base64;

    private String privateKeyPkcs8Base64;

    private Integer enabled;
}
