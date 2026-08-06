package com.scott.payment.merchant.dto.profile;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantProfileResponse
 * @date : 2026-08-01 12:00
 * @email : scott_x@163.com
 * @description : 当前认证商户的主体资料响应，合并共享缓存字段和仅从主库读取的敏感联系字段
 * @status : create
 */
@Data
public class MerchantProfileResponse {

    /** 平台商户号，只读且不可由商户端修改。 */
    private String merchantId;

    /** 商户法定主体名称，只读。 */
    private String merchantName;

    /** 账单或渠道侧展示描述，允许商户按规则维护。 */
    private String billingDescriptor;

    /** 商户简称，允许商户维护。 */
    private String merchantShortName;

    /** 商户状态：1 正常，2 冻结，3 关闭；只读。 */
    private Integer merchantStatus;

    /** Merchant portal and email default locale. */
    private String defaultLocale;

    /** 商户类别码 MCC，只读。 */
    private String merchantCategoryCode;

    /** 商户所属国家三字码，只读。 */
    private String countryCode;

    /** 商户经营区域代码，允许商户维护。 */
    private String regionCode;

    /** 商户经营城市，允许商户维护。 */
    private String city;

    /** 商户详细经营地址，敏感字段，仅从主库读取。 */
    private String addressLine;

    /** 商户经营地址邮编，允许商户维护。 */
    private String postalCode;

    /** 商户联系人姓名，敏感字段，仅向当前认证商户返回。 */
    private String contactName;

    /** 商户联系邮箱，敏感字段，仅向当前认证商户返回。 */
    private String contactEmail;

    /** 商户联系电话，敏感字段，仅向当前认证商户返回。 */
    private String contactPhone;

    /** 默认结算币种三字码，只读。 */
    private String settlementCurrency;

    /** 商户业务时区，允许商户维护。 */
    private String timezone;

    /** 平台风险等级，只读。 */
    private Integer riskLevel;

    /** 商户资料创建时间，精度为毫秒。 */
    private LocalDateTime gmtCreate;

    /** 商户资料最近修改时间，精度为毫秒。 */
    private LocalDateTime gmtModified;
}
