package com.scott.payment.admin.dto.merchant;

import jakarta.validation.constraints.Email;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminMerchantSaveRequest
 * @date : 2026-06-19 22:07
 * @email : scott_x@163.com
 * @description : 管理后台商户基础信息保存请求 DTO
 * @status : create
 *
 * <p>用于新增或更新商户基础档案，承载商户识别信息、联系信息、地区信息和结算信息。</p>
 */
@Data
public class AdminMerchantSaveRequest {

    /**
     * 商户号，作为商户稳定业务标识；新增时由服务端生成，编辑时用于校验不可变更。
     */
    private String merchantId;

    /**
     * 商户主体名称，必须使用英文、数字、空格及常见英文符号，禁止中文。
     */
    @NotBlank(message = "商户名称不能为空")
    @Pattern(regexp = "^[\\x20-\\x7E]{1,128}$", message = "商户名称仅支持英文、数字、空格及常见英文符号")
    private String merchantName;

    /**
     * 账单描述，用于交易账单或渠道侧展示的商户识别名称，禁止中文。
     */
    @Pattern(regexp = "^[\\x20-\\x7E]{1,64}$", message = "账单描述仅支持英文、数字、空格及常见英文符号")
    private String billingDescriptor;

    /**
     * 商户简称。
     */
    @NotBlank(message = "商户简称不能为空")
    private String merchantShortName;

    /**
     * 商户 MCC 类目编码。
     */
    private String merchantCategoryCode;

    /**
     * 商户归属国家代码。
     */
    @NotBlank(message = "国家代码不能为空")
    private String countryCode;

    /** 商户主体类型，例如 COMPANY 或 SOLE_TRADER；创建商户时不允许为空。 */
    @NotBlank(message = "商户类型不能为空")
    private String merchantType;

    /** 实际经营国家 ISO 3166-1 alpha-3 代码；提交审核前不允许为空。 */
    private String operatingCountry;

    /** 面向商户的业务类型编码；创建商户时不允许为空。 */
    @NotBlank(message = "业务类型不能为空")
    private String businessType;

    /** 面向商户的行业分类编码；创建商户时不允许为空。 */
    @NotBlank(message = "行业分类不能为空")
    private String industryCategory;

    /** 主营业务描述；提交审核前不允许为空。 */
    private String merchantDescription;

    /** 公司注册号；企业主体提交审核前不允许为空。 */
    private String registrationNumber;

    /** 企业注册类型，例如 LTD、LLC 或 PARTNERSHIP。 */
    private String legalEntityType;

    /** 企业成立日期；提交审核前不允许为空。 */
    private LocalDate incorporationDate;

    /** 注册证书签发国家 ISO 3166-1 alpha-3 代码。 */
    private String incorporationCountry;

    /** 注册地址州或省；国家不适用时允许为空。 */
    private String registeredState;

    /** 注册地址城市；提交审核前不允许为空。 */
    private String registeredCity;

    /** 注册地址邮编；国家不适用时允许为空。 */
    private String registeredPostcode;

    /** 注册详细地址；提交审核前不允许为空。 */
    private String registeredAddress;

    /** 经营地址是否与注册地址一致；草稿阶段允许为空。 */
    private Boolean operatingSameAsRegistered;

    /** 税号、EIN 或 TIN；按注册国家规则条件采集，属于敏感企业信息。 */
    private String taxId;

    /** 公司规模区间，例如 1_10 或 11_50；可为空。 */
    private String companySize;

    /** 员工人数；单位人数，可为空且不能小于 0。 */
    @DecimalMin(value = "0", message = "员工人数不能小于0")
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
     * 商户联系人姓名；草稿阶段允许为空，提交审核前不允许为空。
     */
    private String contactName;

    /** 主要联系人职位；可为空。 */
    private String contactTitle;

    /** 联系电话国家区号，例如 +86；提交审核前不允许为空。 */
    private String phoneCountryCode;

    /**
     * 联系人邮箱，属于敏感联系信息；草稿阶段允许为空，提交审核前不允许为空。
     */
    @Email(message = "联系邮箱格式不正确")
    private String contactEmail;

    /**
     * 联系人手机号，属于敏感联系信息，可为空。
     */
    private String contactPhone;

    /** 备用联系邮箱；敏感，可为空。 */
    @Email(message = "备用邮箱格式不正确")
    private String alternateEmail;

    /** 财务联系人姓名；可为空，不阻塞提交审核。 */
    private String financeContactName;

    /** 财务联系人邮箱；敏感，可为空。 */
    @Email(message = "财务联系人邮箱格式不正确")
    private String financeContactEmail;

    /** 技术联系人姓名；API 商户建议填写，可为空。 */
    private String technicalContactName;

    /** 技术联系人邮箱；敏感，可为空。 */
    @Email(message = "技术联系人邮箱格式不正确")
    private String technicalContactEmail;

    /** B2C、B2B 或 B2B2C 等业务模式；提交审核前不允许为空。 */
    private String businessModel;

    /** 销售渠道编码集合；数组传输，服务端以逗号分隔持久化。 */
    private List<String> salesChannels = new ArrayList<>();

    /** 主营产品或服务；提交审核前不允许为空。 */
    private String productsServices;

    /** 目标市场国家代码集合；数组传输，服务端以逗号分隔持久化。 */
    private List<String> targetMarkets = new ArrayList<>();

    /** 客户类型，例如 CONSUMER 或 BUSINESS。 */
    private String customerType;

    /** 预计交易币种集合；使用 ISO 4217 三位代码。 */
    private List<String> transactionCurrencies = new ArrayList<>();

    /** 预计月交易金额；单位由 expectedVolumeCurrency 指定，不能小于 0。 */
    @DecimalMin(value = "0", message = "预计月交易金额不能小于0")
    private BigDecimal expectedMonthlyVolume;

    /** 预计月交易金额币种；ISO 4217 三位代码。 */
    private String expectedVolumeCurrency;

    /** 平均单笔金额；单位由 expectedVolumeCurrency 指定，不能小于 0。 */
    @DecimalMin(value = "0", message = "平均单笔金额不能小于0")
    private BigDecimal averageTicket;

    /** 最大单笔金额预估；单位由 expectedVolumeCurrency 指定，可为空且不能小于 0。 */
    @DecimalMin(value = "0", message = "最大单笔金额不能小于0")
    private BigDecimal maxTicket;

    /** 预计月交易笔数；单位笔，可为空且不能小于 0。 */
    @DecimalMin(value = "0", message = "预计月交易笔数不能小于0")
    private Integer expectedMonthlyCount;

    /** 预计退款率；单位百分比，可为空且不能小于 0。 */
    @DecimalMin(value = "0", message = "预计退款率不能小于0")
    private BigDecimal expectedRefundRate;

    /** 预计拒付率；单位百分比，可为空且不能小于 0。 */
    @DecimalMin(value = "0", message = "预计拒付率不能小于0")
    private BigDecimal expectedChargebackRate;

    /** 是否包含订阅或循环扣款；提交审核前应明确回答。 */
    private Boolean recurringPaymentFlag;

    /** 是否包含预售；提交审核前应明确回答。 */
    private Boolean presaleFlag;

    /** 发货或服务交付周期，单位天；条件必填且不能小于 0。 */
    @DecimalMin(value = "0", message = "交付天数不能小于0")
    private Integer fulfillmentDays;

    /** 是否涉及数字商品；提交审核前应明确回答。 */
    private Boolean digitalGoodsFlag;

    /** 是否涉及受限行业；提交审核前应明确回答。 */
    private Boolean restrictedBusinessFlag;

    /** 预计上线日期；可为空。 */
    private LocalDate expectedGoLiveDate;

    /** 官方网站地址；网站已上线时提交审核前不允许为空。 */
    private String websiteUrl;

    /** App Store 页面地址；可为空。 */
    private String appStoreUrl;

    /** Google Play 页面地址；可为空。 */
    private String googlePlayUrl;

    /** 其他销售页面地址；无上线网站时提交审核前不允许为空。 */
    private String otherSalesUrl;

    /** 网站语言编码集合；数组传输，服务端以逗号分隔持久化。 */
    private List<String> websiteLanguages = new ArrayList<>();

    /** 网站是否已经上线；提交审核前应明确回答。 */
    private Boolean websiteLiveFlag;

    /** 隐私政策地址；网站已上线时提交审核前不允许为空。 */
    private String privacyPolicyUrl;

    /** 退款政策地址；网站已上线时提交审核前不允许为空。 */
    private String refundPolicyUrl;

    /** 服务条款地址；网站已上线时提交审核前不允许为空。 */
    private String termsUrl;

    /** 配送政策地址；实物商品场景可按审核规则要求填写。 */
    private String shippingPolicyUrl;

    /** 法人、董事、UBO 与授权人列表；草稿允许为空，提交审核前按主体类型校验。 */
    @Valid
    private List<MerchantOnboardingDTOs.RelatedPerson> relatedPersons = new ArrayList<>();

    /**
     * 结算币种代码。
     */
    private String settlementCurrency;

    /**
     * 商户业务时区，例如 Asia/Shanghai。
     */
    @NotBlank(message = "时区不能为空")
    private String timezone;

    /**
     * 商户状态。
     */
    private Integer merchantStatus;

    /** 商户默认语言，仅支持 zh-CN 和 en-US。 */
    @NotBlank(message = "默认语言不能为空")
    private String defaultLocale;

    /**
     * 商户风险等级，可为空；为空时由服务端填充默认值。
     */
    private Integer riskLevel;
}
