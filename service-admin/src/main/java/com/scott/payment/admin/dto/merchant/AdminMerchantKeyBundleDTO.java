package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantKeyBundleDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户管理Admin Merchant Key Bundle 数据传输对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
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
