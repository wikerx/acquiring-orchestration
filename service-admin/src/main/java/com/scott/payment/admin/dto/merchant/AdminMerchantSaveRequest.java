package com.scott.payment.admin.dto.merchant;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 商户基础信息保存请求。
 */
@Data
public class AdminMerchantSaveRequest {

    @NotBlank(message = "商户号不能为空")
    private String merchantId;

    @NotBlank(message = "商户名称不能为空")
    private String merchantName;

    private String merchantShortName;

    @NotBlank(message = "MCC不能为空")
    private String merchantCategoryCode;

    @NotBlank(message = "国家代码不能为空")
    private String countryCode;

    private String regionCode;

    private String city;

    private String addressLine;

    private String contactEmail;

    private String contactPhone;

    @NotBlank(message = "结算币种不能为空")
    private String settlementCurrency;

    @NotBlank(message = "时区不能为空")
    private String timezone;

    private Integer merchantStatus;

    private Integer riskLevel;
}
