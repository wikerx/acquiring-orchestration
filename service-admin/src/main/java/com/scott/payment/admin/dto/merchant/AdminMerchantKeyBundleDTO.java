package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 管理后台商户 OpenAPI 密钥集合响应 DTO。
 * <p>
 * 用于展示某个商户当前密钥摘要列表，不承载密钥原文。
 */
@Data
public class AdminMerchantKeyBundleDTO {

    /**
     * 商户号。
     */
    private String merchantId;

    /**
     * 商户名称。
     */
    private String merchantName;

    /**
     * 商户全部密钥材料列表，空列表表示当前尚未生成任何材料。
     */
    private List<AdminMerchantKeyMaterialDTO> keys = new ArrayList<>();
}
