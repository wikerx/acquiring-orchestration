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
 * 管理端商户资料变更审核 DTO 集合，承载查询、详情、快照和审核决定
 */
public final class AdminMerchantProfileChangeDTOs {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private AdminMerchantProfileChangeDTOs() {
    }

    /**
     * 管理端资料变更申请分页查询条件。
     */
    @Data
    public static class Query {
        /**
         * 查询页码，从 1 开始。
         */
        private long pageNo = 1;
        /**
         * 每页记录数，由接口上限约束避免无界查询。
         */
        private long pageSize = 20;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;
        private String status;
    }

    /**
     * 管理端资料变更审核决定。
     */
    @Data
    public static class ReviewRequest {
        /**
         * 请求中的结论，用于限定本次操作的输入和校验范围。
         */
        private String decision;
        private String comment;
    }

    /**
     * 资料变更申请详情，敏感证件号只返回脱敏值。
     */
    @Data
    public static class ChangeRequest {
        /**
         * 请求中的请求编号，用于限定本次操作的输入和校验范围。
         */
        private String requestNo;
        /**
         * 商户号，用于限定商户配置、交易数据、风控规则和权限归属。
         */
        private String merchantId;
        private String merchantName;
        private String status;
        private List<String> changedFields = new ArrayList<>();
        /**
         * 请求中的当前资料，用于限定本次操作的输入和校验范围。
         */
        private ProfileSnapshot currentProfile;
        private ProfileSnapshot proposedProfile;
        private List<Document> documents = new ArrayList<>();
        private String submitComment;
        private String reviewComment;
        private String submittedBy;
        private String reviewedBy;
        private LocalDateTime submittedAt;
        private LocalDateTime reviewedAt;
        private LocalDateTime gmtCreate;
        private LocalDateTime gmtModified;
    }

    /**
     * 需审核的商户高风险资料快照。
     */
    @Data
    public static class ProfileSnapshot {
        private String merchantName;
        private String billingDescriptor;
        private String merchantType;
        private String countryCode;
        private String operatingCountry;
        /**
         * 业务线类型，用于区分收单、代付等业务域，并隔离渠道配置、限额规则和后台查询口径。
         */
        private String businessType;
        private String industryCategory;
        private String merchantDescription;
        private String registrationNumber;
        private String legalEntityType;
        private LocalDate incorporationDate;
        private String incorporationCountry;
        private String registeredState;
        private String registeredCity;
        private String registeredPostcode;
        private String registeredAddress;
        private Boolean operatingSameAsRegistered;
        private String taxId;
        private String companySize;
        private Integer employeeCount;
        private String regionCode;
        /**
         * 城市，表示账单、收货或商户地址组成部分，展示和日志输出必须脱敏。
         */
        private String city;
        private String addressLine;
        private String postalCode;
        private String businessModel;
        private List<String> salesChannels = new ArrayList<>();
        private String productsServices;
        private List<String> targetMarkets = new ArrayList<>();
        private String customerType;
        private List<String> transactionCurrencies = new ArrayList<>();
        private BigDecimal expectedMonthlyVolume;
        private String expectedVolumeCurrency;
        private BigDecimal averageTicket;
        private BigDecimal maxTicket;
        private Integer expectedMonthlyCount;
        private BigDecimal expectedRefundRate;
        private BigDecimal expectedChargebackRate;
        private Boolean recurringPaymentFlag;
        private Boolean presaleFlag;
        private Integer fulfillmentDays;
        private Boolean digitalGoodsFlag;
        private Boolean restrictedBusinessFlag;
        private LocalDate expectedGoLiveDate;
        private String websiteUrl;
        private String appStoreUrl;
        private String googlePlayUrl;
        private String otherSalesUrl;
        private List<String> websiteLanguages = new ArrayList<>();
        private Boolean websiteLiveFlag;
        private String privacyPolicyUrl;
        private String refundPolicyUrl;
        private String termsUrl;
        private String shippingPolicyUrl;
        private List<MerchantOnboardingDTOs.RelatedPerson> relatedPersons = new ArrayList<>();
        private LocalDateTime capturedAt;
    }

    /**
     * 变更申请关联资料元数据。
     */
    @Data
    public static class Document {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String documentType;
        private String originalFilename;
        /**
         * 内容类型，用于区分文本、HTML、附件、JSON 或导出文件等载荷格式。
         */
        private String contentType;
        private Long fileSize;
        private String sha256;
        private String documentStatus;
        private LocalDateTime gmtCreate;
    }
}
