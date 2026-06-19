package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantKeyBundleDTO
 * @date : 2026-06-19 22:05
 * @email : scott_x@163.com
 * @description : 管理后台商户 OpenAPI 密钥集合响应 DTO
 * @status : create
 *
 * <p>用于展示某个商户当前可见的全部密钥材料摘要与详情列表。</p>
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
