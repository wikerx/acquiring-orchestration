package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantKeyBundleDTO
 * @date : 2026-06-13 17:17
 * @email : scott_x@163.com
 * @description : Admin Merchant Key Bundle DTO 传输模型，位于 运营后台服务，定义接口或跨服务调用字段，承载标识、状态、金额、配置或响应摘要，不直接执行业务逻辑。
 * @status : create
 */
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
