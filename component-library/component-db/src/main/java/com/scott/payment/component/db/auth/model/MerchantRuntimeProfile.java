package com.scott.payment.component.db.auth.model;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRuntimeProfile
 * @date : 2026-08-01 12:00
 * @email : scott_x@163.com
 * @description : 跨 Admin、Merchant Portal 与 OpenAPI 共享的商户非敏感运行资料缓存模型，数据库商户主表始终是最终事实源
 * @status : update
 *
 * <p>该对象使用永久 Redis Key {@code merchant:info:{merchantId}}。允许保存交易、登录和商户门户
 * 共用的主体展示及运行配置，但禁止加入联系人、详细地址、JWT Secret、API Secret、RSA 私钥、
 * AES 密钥或其他敏感材料。</p>
 */
@Data
public class MerchantRuntimeProfile {

    /** 当前缓存结构修订号；只用于兼容刷新，不进入 Redis Key。 */
    public static final int CURRENT_CACHE_SCHEMA_REVISION = 2;

    /**
     * 缓存结构修订号。历史永久 Value 缺少该字段时反序列化为 0，读取门面会精确刷新当前商户。
     */
    private int cacheSchemaRevision;

    /**
     * 商户主表主键。
     */
    private Long id;

    /**
     * 平台商户号。
     */
    private String merchantId;

    /** 商户主体名称；不包含联系人姓名等个人信息。 */
    private String merchantName;

    /** 账单或渠道侧使用的商户描述。 */
    private String billingDescriptor;

    /** 商户简称，用于后台和商户门户展示。 */
    private String merchantShortName;

    /**
     * 商户状态：1 正常，2 冻结，3 关闭。
     */
    private Integer merchantStatus;

    /**
     * 商户类别码。
     */
    private String merchantCategoryCode;

    /**
     * 商户所在国家三字码。
     */
    private String countryCode;

    /** 商户经营区域代码；不包含详细街道地址。 */
    private String regionCode;

    /** 商户经营城市；不包含联系人或详细街道地址。 */
    private String city;

    /** 商户经营地址邮编。 */
    private String postalCode;

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

    /** 数据库商户资料最近修改时间，用于页面展示和问题追踪。 */
    private LocalDateTime gmtModified;

    /**
     * 判断反序列化后的 Value 是否符合当前缓存结构。
     *
     * @return true 表示可直接使用，false 表示需要按商户号精确回源刷新
     */
    public boolean hasCurrentCacheSchema() {
        return cacheSchemaRevision == CURRENT_CACHE_SCHEMA_REVISION;
    }
}
