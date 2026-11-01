package com.scott.payment.merchant.dto.profile;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 商户资料变更申请接口模型集合，承载敏感资料快照、关联人员和审核状态。
 */
public final class MerchantProfileChangeDTOs {

    /**
     * 禁止实例化商户资料变更 DTO 聚合类。
     */
    private MerchantProfileChangeDTOs() {
    }

    /**
     * 商户需要审核后生效的完整资料快照。
     */
    @Data
    public static class ProfileSnapshot {
        @NotBlank private String merchantName;
        private String billingDescriptor;
        @NotBlank private String merchantType;
        @NotBlank private String countryCode;
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
        @DecimalMin("0") private Integer employeeCount;
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
        @DecimalMin("0") private BigDecimal expectedMonthlyVolume;
        private String expectedVolumeCurrency;
        @DecimalMin("0") private BigDecimal averageTicket;
        @DecimalMin("0") private BigDecimal maxTicket;
        @DecimalMin("0") private Integer expectedMonthlyCount;
        @DecimalMin("0") @DecimalMax("100") private BigDecimal expectedRefundRate;
        @DecimalMin("0") @DecimalMax("100") private BigDecimal expectedChargebackRate;
        private Boolean recurringPaymentFlag;
        private Boolean presaleFlag;
        @DecimalMin("0") private Integer fulfillmentDays;
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
        @Valid private List<RelatedPerson> relatedPersons = new ArrayList<>();
        private LocalDateTime capturedAt;
    }

    /**
     * 法人、董事、UBO 或授权人资料。
     */
    @Data
    public static class RelatedPerson {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        @NotBlank private String fullName;
        private List<String> personRoles = new ArrayList<>();
        private String nationality;
        private LocalDate dateOfBirth;
        private String residenceCountry;
        private String residentialAddress;
        private String idType;
        private String idNumber;
        private String idNumberMasked;
        private LocalDate idExpiryDate;
        @DecimalMin("0") @DecimalMax("100") private BigDecimal ownershipPercentage;
        private Boolean controllerFlag;
        private Boolean pepFlag;
        /**
         * 电话，表示业务联系人或付款人的电话号码，展示和日志输出必须脱敏。
         */
        @Email private String email;
        private String phone;
    }

    /**
     * 商户资料变更申请响应。
     */
    @Data
    public static class ChangeRequest {
        private String requestNo;
        private String merchantId;
        private String status;
        private List<String> changedFields = new ArrayList<>();
        private ProfileSnapshot proposedProfile;
        private String submitComment;
        private String reviewComment;
        private LocalDateTime submittedAt;
        private LocalDateTime reviewedAt;
        private LocalDateTime gmtCreate;
        private LocalDateTime gmtModified;
    }

    /**
     * 商户合规资料元数据，禁止包含 Bucket、对象键或访问凭证。
     */
    @Data
    public static class Document {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        /**
         * 文件关联的资料变更申请编号；正式资料文件可为空。
         */
        private String requestNo;
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

    /**
     * 商户资料工作区响应，汇总正式资料、活动申请、历史申请和合规资料。
     */
    @Data
    public static class Workspace {
        /**
         * 当前已生效的商户资料快照。
         */
        private ProfileSnapshot currentProfile;
        private ChangeRequest activeRequest;
        private List<ChangeRequest> requestHistory = new ArrayList<>();
        private List<Document> documents = new ArrayList<>();
        private List<String> completenessIssues = new ArrayList<>();
        private Integer completenessPercent;
    }

    /**
     * 商户提交资料变更审核时的可选说明。
     */
    @Data
    public static class SubmitRequest {
        private String comment;
    }
}
