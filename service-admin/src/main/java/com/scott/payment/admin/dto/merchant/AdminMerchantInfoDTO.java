package com.scott.payment.admin.dto.merchant;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantInfoDTO
 * @date : 2026-06-19 22:05
 * @email : scott_x@163.com
 * @description : 管理后台商户完整资料响应 DTO，包含开户状态、KYB、业务画像、相关人员和就绪检查结果
 * @status : create
 *
 * <p>用于管理后台商户列表与详情展示。列表仅填充主档和密钥摘要；详情额外填充相关人员、
 * 合规资料、审核历史和账号、资金账户、费率就绪状态。</p>
 */
@Data
public class AdminMerchantInfoDTO {

    /**
     * 商户资料主键 ID。
     *
     * <p>后台主键是雪花 Long，超过 JavaScript 安全整数范围；接口返回字符串，避免前端编辑时 ID 被精度截断。</p>
     */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /**
     * 商户号，作为外部接入与后台检索的稳定业务标识。
     */
    private String merchantId;

    /** 开户申请编号；系统生成，不允许前端修改。 */
    private String applicationNo;

    /** 开户来源，例如 ADMIN 或 IMPORT。 */
    private String onboardingSource;

    /** 开户流程状态，例如 DRAFT、PENDING_REVIEW、APPROVED 或 ACTIVE。 */
    private String onboardingStatus;

    /** 审核状态，例如 NOT_SUBMITTED、PENDING、SUPPLEMENT、PASSED 或 REJECTED。 */
    private String reviewStatus;

    /** 激活状态，例如 NOT_READY、READY 或 ACTIVE。 */
    private String activationStatus;

    /**
     * 商户主体名称，必须使用英文、数字、空格及常见英文符号。
     */
    private String merchantName;

    /**
     * 账单描述，用于交易账单或渠道侧展示的商户识别名称。
     */
    private String billingDescriptor;

    /**
     * 商户简称，可为空。
     */
    private String merchantShortName;

    /** 商户主体类型，例如 COMPANY 或 SOLE_TRADER。 */
    private String merchantType;

    /**
     * 商户状态，通常用于区分启用、停用等业务状态。
     */
    private Integer merchantStatus;

    /** 商户系统和商户通知默认语言。 */
    private String defaultLocale;

    /**
     * 商户 MCC 类目编码。
     */
    private String merchantCategoryCode;

    /**
     * 商户归属国家代码，通常为 ISO 标准编码。
     */
    private String countryCode;

    /** 实际经营国家 ISO 3166-1 alpha-3 代码。 */
    private String operatingCountry;

    /** 面向商户的业务类型编码。 */
    private String businessType;

    /** 面向商户的行业分类编码。 */
    private String industryCategory;

    /** 主营业务描述。 */
    private String merchantDescription;

    /** 公司注册号；属于敏感企业信息。 */
    private String registrationNumber;

    /** 企业注册类型，例如 LTD、LLC 或 PARTNERSHIP。 */
    private String legalEntityType;

    /** 企业成立日期。 */
    private LocalDate incorporationDate;

    /** 注册证书签发国家 ISO 3166-1 alpha-3 代码。 */
    private String incorporationCountry;

    /** 注册地址州或省。 */
    private String registeredState;

    /** 注册地址城市。 */
    private String registeredCity;

    /** 注册地址邮编。 */
    private String registeredPostcode;

    /** 注册详细地址。 */
    private String registeredAddress;

    /** 经营地址是否与注册地址一致；null 表示草稿阶段尚未回答。 */
    private Boolean operatingSameAsRegistered;

    /** 税号、EIN 或 TIN；属于敏感企业信息。 */
    private String taxId;

    /** 公司规模区间。 */
    private String companySize;

    /** 员工人数，单位人数。 */
    private Integer employeeCount;

    /**
     * 商户归属地区编码，可为空。
     */
    private String regionCode;

    /**
     * 商户所在城市，可为空。
     */
    private String city;

    /**
     * 商户详细地址，可为空。
     */
    private String addressLine;

    /**
     * 商户经营地址邮编，可为空。
     */
    private String postalCode;

    /**
     * 商户联系人姓名，可为空。
     */
    private String contactName;

    /** 主要联系人职位。 */
    private String contactTitle;

    /** 联系电话国家区号，例如 +86。 */
    private String phoneCountryCode;

    /**
     * 商户联系人邮箱，属于敏感联系信息，展示时应按需要脱敏。
     */
    private String contactEmail;

    /**
     * 商户联系人手机号，属于敏感联系信息，展示时应按需要脱敏。
     */
    private String contactPhone;

    /** 备用联系邮箱；敏感，可为空。 */
    private String alternateEmail;

    /** 财务联系人姓名；可为空。 */
    private String financeContactName;

    /** 财务联系人邮箱；敏感，可为空。 */
    private String financeContactEmail;

    /** 技术联系人姓名；可为空。 */
    private String technicalContactName;

    /** 技术联系人邮箱；敏感，可为空。 */
    private String technicalContactEmail;

    /** B2C、B2B 或 B2B2C 等业务模式。 */
    private String businessModel;

    /** 销售渠道编码集合，例如 WEBSITE、APP 或 MARKETPLACE。 */
    private List<String> salesChannels = new ArrayList<>();

    /** 主营产品或服务。 */
    private String productsServices;

    /** 目标市场国家代码集合。 */
    private List<String> targetMarkets = new ArrayList<>();

    /** 客户类型，例如 CONSUMER 或 BUSINESS。 */
    private String customerType;

    /** 预计交易币种集合，使用 ISO 4217 三位代码。 */
    private List<String> transactionCurrencies = new ArrayList<>();

    /** 预计月交易金额；单位由 expectedVolumeCurrency 指定。 */
    private BigDecimal expectedMonthlyVolume;

    /** 预计月交易金额币种，使用 ISO 4217 三位代码。 */
    private String expectedVolumeCurrency;

    /** 平均单笔金额；单位由 expectedVolumeCurrency 指定。 */
    private BigDecimal averageTicket;

    /** 最大单笔金额预估；单位由 expectedVolumeCurrency 指定。 */
    private BigDecimal maxTicket;

    /** 预计月交易笔数，单位笔。 */
    private Integer expectedMonthlyCount;

    /** 预计退款率，单位百分比。 */
    private BigDecimal expectedRefundRate;

    /** 预计拒付率，单位百分比。 */
    private BigDecimal expectedChargebackRate;

    /** 是否包含订阅或循环扣款；null 表示草稿阶段尚未回答。 */
    private Boolean recurringPaymentFlag;

    /** 是否包含预售；null 表示草稿阶段尚未回答。 */
    private Boolean presaleFlag;

    /** 发货或服务交付周期，单位天。 */
    private Integer fulfillmentDays;

    /** 是否涉及数字商品；null 表示草稿阶段尚未回答。 */
    private Boolean digitalGoodsFlag;

    /** 是否涉及受限行业；null 表示草稿阶段尚未回答。 */
    private Boolean restrictedBusinessFlag;

    /** 预计上线日期。 */
    private LocalDate expectedGoLiveDate;

    /** 官方网站地址。 */
    private String websiteUrl;

    /** App Store 页面地址。 */
    private String appStoreUrl;

    /** Google Play 页面地址。 */
    private String googlePlayUrl;

    /** 其他销售页面地址。 */
    private String otherSalesUrl;

    /** 网站语言编码集合。 */
    private List<String> websiteLanguages = new ArrayList<>();

    /** 网站是否已经上线；null 表示草稿阶段尚未回答。 */
    private Boolean websiteLiveFlag;

    /** 隐私政策地址。 */
    private String privacyPolicyUrl;

    /** 退款政策地址。 */
    private String refundPolicyUrl;

    /** 服务条款地址。 */
    private String termsUrl;

    /** 配送政策地址。 */
    private String shippingPolicyUrl;

    /** 法人、董事、UBO 与授权人列表；仅详情接口填充。 */
    private List<MerchantOnboardingDTOs.RelatedPerson> relatedPersons = new ArrayList<>();

    /** 合规资料元数据列表；仅详情接口填充，不包含存储定位和正文。 */
    private List<MerchantOnboardingDTOs.Document> documents = new ArrayList<>();

    /** 审核历史列表；按创建时间倒序返回。 */
    private List<MerchantOnboardingDTOs.ReviewRecord> reviewRecords = new ArrayList<>();

    /** 当前资料能否提交审核。 */
    private Boolean reviewSubmittable;

    /** 当前配置能否激活。 */
    private Boolean activationReady;

    /** 阻塞提交或激活的稳定问题编码；由前端按当前语言转换为展示文案。 */
    private List<String> readinessIssues = new ArrayList<>();

    /**
     * 商户结算币种代码。
     */
    private String settlementCurrency;

    /**
     * 商户业务时区，例如 Asia/Shanghai。
     */
    private String timezone;

    /**
     * 商户风险等级，用于后台风险分层管理。
     */
    private Integer riskLevel;

    /**
     * 记录创建时间。
     */
    private LocalDateTime gmtCreate;

    /**
     * 最近更新时间。
     */
    private LocalDateTime gmtModified;

    /**
     * 当前生效的 JWT 密钥摘要，可为空。
     */
    private AdminMerchantKeySummaryDTO jwtKey;

    /**
     * 当前生效的平台请求体密钥摘要，可为空。
     */
    private AdminMerchantKeySummaryDTO platformPayloadKey;

    /**
     * 当前生效的商户响应密钥摘要，可为空。
     */
    private AdminMerchantKeySummaryDTO responseKey;

    /** 是否已创建商户系统登录账号；历史商户未初始化时为 false。 */
    private Boolean loginInitialized;

    /** 当前单结算币种资金账户号；尚未开户时为空。 */
    private String fundAccountNo;

    /** 当前资金账户状态；尚未开户时为空。 */
    private String fundAccountStatus;

    /** 当前已生效商户费率版本号；尚未配置时为空。 */
    private Integer currentFeeVersionNo;
}
