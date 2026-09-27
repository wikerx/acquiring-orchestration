package com.scott.payment.component.db.auth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BaseMerchantInfoDO
 * @date : 2026-06-06 00:00
 * @email : scott_x@163.com
 * @description : 商户基础信息轻量实体，用于商户系统账号绑定校验
 * @status : create
 */
@Data
@TableName("base_merchant_info")
public class BaseMerchantInfoDO {

    /**
     * 主键ID。
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 支付框架颁发的商户号。
     */
    private String merchantId;

    /** 开户申请编号。 */
    private String applicationNo;

    /** 开户来源：ADMIN、SELF_REGISTER 或 IMPORT。 */
    private String onboardingSource;

    /** 开户流程状态。 */
    private String onboardingStatus;

    /** 审核状态。 */
    private String reviewStatus;

    /** 激活状态。 */
    private String activationStatus;

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

    /** 商户主体类型。 */
    private String merchantType;

    /**
     * 商户状态。1 表示正常，2 表示冻结，3 表示关闭。
     */
    private Integer merchantStatus;

    /** Default locale for merchant-facing emails and portal preferences. */
    private String defaultLocale;

    /**
     * 商户类别码 MCC。
     */
    private String merchantCategoryCode;

    /**
     * 国家三字码。
     */
    private String countryCode;

    /** 实际经营国家三字码。 */
    private String operatingCountry;

    /** 面向商户的业务类型。 */
    private String businessType;

    /** 面向商户的行业分类。 */
    private String industryCategory;

    /** 主营业务描述。 */
    private String merchantDescription;

    /** 公司注册号。 */
    private String registrationNumber;

    /** 企业注册类型。 */
    private String legalEntityType;

    /** 企业成立日期。 */
    private LocalDate incorporationDate;

    /** 注册证书签发国家三字码。 */
    private String incorporationCountry;

    /** 注册地址州省。 */
    private String registeredState;

    /** 注册地址城市。 */
    private String registeredCity;

    /** 注册地址邮编。 */
    private String registeredPostcode;

    /** 注册详细地址。 */
    private String registeredAddress;

    /** 经营地址是否与注册地址一致。 */
    private Integer operatingSameAsRegistered;

    /** 税号、EIN 或 TIN。 */
    private String taxId;

    /** 公司规模区间。 */
    private String companySize;

    /** 员工人数。 */
    private Integer employeeCount;

    /**
     * 区域代码。
     */
    private String regionCode;

    /**
     * 城市。
     */
    private String city;

    /**
     * 地址。
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

    /** 主要联系人职位。 */
    private String contactTitle;

    /** 历史兼容字段；新数据不再单独保存电话国家区号。 */
    private String phoneCountryCode;

    /**
     * 联系邮箱。
     */
    private String contactEmail;

    /** 包含国家区号的完整国际电话号码。 */
    private String contactPhone;

    /** 备用联系邮箱。 */
    private String alternateEmail;

    /** 财务联系人姓名。 */
    private String financeContactName;

    /** 财务联系人邮箱。 */
    private String financeContactEmail;

    /** 技术联系人姓名。 */
    private String technicalContactName;

    /** 技术联系人邮箱。 */
    private String technicalContactEmail;

    /** B2C、B2B 或 B2B2C 等业务模式。 */
    private String businessModel;

    /** 销售渠道，多值使用逗号分隔。 */
    private String salesChannels;

    /** 主营产品或服务。 */
    private String productsServices;

    /** 目标市场，多值使用逗号分隔。 */
    private String targetMarkets;

    /** 客户类型。 */
    private String customerType;

    /** 预计交易币种，多值使用逗号分隔。 */
    private String transactionCurrencies;

    /** 预计月交易金额。 */
    private BigDecimal expectedMonthlyVolume;

    /** 预计月交易金额币种。 */
    private String expectedVolumeCurrency;

    /** 平均单笔金额。 */
    private BigDecimal averageTicket;

    /** 最大单笔金额预估。 */
    private BigDecimal maxTicket;

    /** 预计月交易笔数。 */
    private Integer expectedMonthlyCount;

    /** 预计退款率百分比。 */
    private BigDecimal expectedRefundRate;

    /** 预计拒付率百分比。 */
    private BigDecimal expectedChargebackRate;

    /** 是否包含订阅或循环扣款。 */
    private Integer recurringPaymentFlag;

    /** 是否包含预售。 */
    private Integer presaleFlag;

    /** 发货或服务交付天数。 */
    private Integer fulfillmentDays;

    /** 是否涉及数字商品。 */
    private Integer digitalGoodsFlag;

    /** 是否涉及受限行业。 */
    private Integer restrictedBusinessFlag;

    /** 预计上线日期。 */
    private LocalDate expectedGoLiveDate;

    /** 官方网站地址。 */
    private String websiteUrl;

    /** App Store 地址。 */
    private String appStoreUrl;

    /** Google Play 地址。 */
    private String googlePlayUrl;

    /** 其他销售页面地址。 */
    private String otherSalesUrl;

    /** 网站语言，多值使用逗号分隔。 */
    private String websiteLanguages;

    /** 网站是否已经上线。 */
    private Integer websiteLiveFlag;

    /** 隐私政策地址。 */
    private String privacyPolicyUrl;

    /** 退款政策地址。 */
    private String refundPolicyUrl;

    /** 服务条款地址。 */
    private String termsUrl;

    /** 配送政策地址。 */
    private String shippingPolicyUrl;

    /**
     * 默认结算币种。
     */
    private String settlementCurrency;

    /**
     * 商户业务时区。
     */
    private String timezone;

    /**
     * 风险等级：1 低，2 中，3 高。
     */
    private Integer riskLevel;

    /**
     * 创建时间。
     */
    private LocalDateTime gmtCreate;

    /**
     * 修改时间。
     */
    private LocalDateTime gmtModified;

    /**
     * 删除标识。
     */
    private Integer deleted;
}
