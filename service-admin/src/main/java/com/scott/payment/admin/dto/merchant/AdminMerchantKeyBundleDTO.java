package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 商户 OpenAPI 密钥集合响应。
 */
@Data
public class AdminMerchantKeyBundleDTO {

    private String merchantId;

    private String merchantName;

    private List<AdminMerchantKeyMaterialDTO> keys = new ArrayList<>();
}
