package com.scott.payment.openapi.dto.security;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantInfoDTO
 * @date : 2026-05-30 09:20
 * @email : scott_x@163.com
 * @description : 商户基础信息查询结果
 * @status : create
 */
@Data
@NoArgsConstructor
public class MerchantInfoDTO {

    /**
     * 支付框架颁发的商户号。
     */
    private String merchantId;

    /**
     * 商户主体名称。
     */
    private String merchantName;

    /**
     * 商户简称。
     */
    private String merchantShortName;

    /**
     * 商户状态。ACTIVE 表示可交易，FROZEN 表示冻结，CLOSED 表示关闭。
     */
    private String merchantStatus;

    /**
     * 商户类别码 MCC。
     */
    private String merchantCategoryCode;

    /**
     * 商户所在国家三字码。
     */
    private String countryCode;

    /**
     * 商户所在州、省或区域代码。
     */
    private String regionCode;

    /**
     * 商户所在城市。
     */
    private String city;

    /**
     * 商户开户地址或经营地址。
     */
    private String addressLine;

    /**
     * 商户联系人邮箱。
     */
    private String contactEmail;

    /**
     * 商户联系人电话。
     */
    private String contactPhone;

    /**
     * 默认结算币种。
     */
    private String settlementCurrency;

    /**
     * 商户业务时区。
     */
    private String timezone;

    /**
     * 商户风险等级。
     */
    private String riskLevel;
}
