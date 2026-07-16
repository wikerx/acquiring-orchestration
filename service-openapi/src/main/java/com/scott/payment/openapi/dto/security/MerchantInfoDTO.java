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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantInfoDTO
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 商户 OpenAPIMerchant Info 数据传输对象，位于 service-openapi 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
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
     * 账单描述，用于交易账单或渠道侧展示的商户识别名称。
     */
    private String billingDescriptor;

    /**
     * 商户简称。
     */
    private String merchantShortName;

    /**
     * 商户状态。1 表示正常可交易，2 表示冻结，3 表示关闭。
     */
    private Integer merchantStatus;

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
     * 商户经营地址邮编。
     */
    private String postalCode;

    /**
     * 商户联系人姓名。
     */
    private String contactName;

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
     * 商户风险等级。1 表示低风险，2 表示普通风险，3 表示高风险。
     */
    private Integer riskLevel;
}
