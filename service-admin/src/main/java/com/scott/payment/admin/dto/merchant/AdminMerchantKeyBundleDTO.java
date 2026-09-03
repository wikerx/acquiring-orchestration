package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantKeyBundleDTO
 * @date : 2026-06-13 17:17
 * @email : scott_x@163.com
 * @description : Admin 商户密钥下载包 DTO，聚合一次性密钥材料、版本、指纹和生成时间，禁止在日志中输出完整内容。
 * @status : create
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
