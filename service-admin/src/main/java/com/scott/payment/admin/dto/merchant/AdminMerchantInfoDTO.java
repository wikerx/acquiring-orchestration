package com.scott.payment.admin.dto.merchant;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 商户基础信息响应。
 */
@Data
public class AdminMerchantInfoDTO {

    private Long id;

    private String merchantId;

    private String merchantName;

    private String merchantShortName;

    private Integer merchantStatus;

    private String merchantCategoryCode;

    private String countryCode;

    private String regionCode;

    private String city;

    private String addressLine;

    private String contactEmail;

    private String contactPhone;

    private String settlementCurrency;

    private String timezone;

    private Integer riskLevel;

    private LocalDateTime gmtCreate;

    private LocalDateTime gmtModified;

    private AdminMerchantKeySummaryDTO jwtKey;

    private AdminMerchantKeySummaryDTO platformPayloadKey;

    private AdminMerchantKeySummaryDTO responseKey;
}
