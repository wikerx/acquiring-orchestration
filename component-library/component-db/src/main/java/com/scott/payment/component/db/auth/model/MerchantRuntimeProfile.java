package com.scott.payment.component.db.auth.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRuntimeProfile
 * @date : 2026-08-01 12:00
 * @email : scott_x@163.com
 * @description : 跨 Admin、Merchant Portal、OpenAPI 与支付服务共享的完整商户资料缓存模型，数据库商户主表始终是最终事实源
 * @status : update
 *
 * <p>该对象使用永久 Redis Key {@code acquiring:{environment}:merchant:info:{merchantId}}，
 * 覆盖 {@code base_merchant_info} 中管理端、商户端和交易入口需要复用的商户资料。
 * 缓存对象与数据库实体隔离，严禁加入 JWT Secret、API Secret、RSA 私钥、AES 密钥等安全材料。</p>
 */
@Data
public class MerchantRuntimeProfile {

    /**
     * 商户主表主键。
     */
    private Long id;

    /**
     * 平台商户号。
     */
    private String merchantId;

    /** 商户主体名称。 */
    private String merchantName;

    /** 账单或渠道侧使用的商户描述。 */
    private String billingDescriptor;

    /** 商户简称，用于后台和商户门户展示。 */
    private String merchantShortName;

    /**
     * 商户状态：1 正常，2 冻结，3 关闭。
     */
    private Integer merchantStatus;

    /** Default locale for merchant-facing notifications. */
    private String defaultLocale;

    /**
     * 商户类别码。
     */
    private String merchantCategoryCode;

    /**
     * 商户所在国家三字码。
     */
    private String countryCode;

    /** 商户经营区域代码。 */
    private String regionCode;

    /** 商户经营城市。 */
    private String city;

    /** 商户经营详细地址，属于受保护资料，禁止写入业务日志。 */
    private String addressLine;

    /** 商户经营地址邮编。 */
    private String postalCode;

    /** 商户联系人姓名，属于受保护资料，禁止写入业务日志。 */
    private String contactName;

    /** 商户联系邮箱，属于受保护资料，禁止写入业务日志。 */
    private String contactEmail;

    /** 商户联系电话，属于受保护资料，禁止写入业务日志。 */
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
    private Integer riskLevel;

    /** 数据库商户资料创建时间。 */
    private LocalDateTime gmtCreate;

    /** 数据库商户资料最近修改时间，用于页面展示和问题追踪。 */
    private LocalDateTime gmtModified;
}
