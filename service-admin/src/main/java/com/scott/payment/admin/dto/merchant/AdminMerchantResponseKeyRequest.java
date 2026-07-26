package com.scott.payment.admin.dto.merchant;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantResponseKeyRequest
 * @date : 2026-06-19 22:07
 * @email : scott_x@163.com
 * @description : 管理后台商户响应密钥维护请求 DTO
 * @status : create
 *
 * <p>用于维护商户自管响应密钥材料，包含响应公钥以及可选的私钥和启用状态。</p>
 */
@Data
public class AdminMerchantResponseKeyRequest {

    /**
     * 商户响应公钥，要求为 X.509 Base64 编码文本。
     */
    @NotBlank(message = "响应公钥不能为空")
    private String publicKeyX509Base64;

    /**
     * 商户响应私钥，要求为 PKCS8 Base64 编码文本，可为空。
     */
    private String privateKeyPkcs8Base64;

    /**
     * 启用状态，可为空；为空时由服务端沿用默认处理策略。
     */
    private Integer enabled;
}
