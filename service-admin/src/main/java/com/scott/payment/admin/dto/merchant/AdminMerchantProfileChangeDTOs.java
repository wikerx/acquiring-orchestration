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
 * @classname : AdminMerchantProfileChangeDTOs
 * @date : 2026-09-21 10:00
 * @email : scott_x@163.com
 * @description : 管理端商户资料变更审核 DTO 集合，承载查询、详情、快照和审核决定
 * @status : create
 */
public final class AdminMerchantProfileChangeDTOs {

    private AdminMerchantProfileChangeDTOs() {
    }

    /** 管理端资料变更申请分页查询条件。 */
    @Data
    public static class Query {
        private long pageNo = 1;
        private long pageSize = 20;
        private String merchantId;
        private String status;
    }

    /** 管理端资料变更审核决定。 */
    @Data
    public static class ReviewRequest {
        private String decision;
        private String comment;
    }

    /** 资料变更申请详情，敏感证件号只返回脱敏值。 */
    @Data
    public static class ChangeRequest {
        private String requestNo;
        private String merchantId;
        private String merchantName;
        private String status;
        private List<String> changedFields = new ArrayList<>();
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

    /** 需审核的商户高风险资料快照。 */
    @Data
    public static class ProfileSnapshot {
        private String merchantName;
        private String billingDescriptor;
        private String merchantType;
        private String countryCode;
        private String operatingCountry;
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

    /** 变更申请关联资料元数据。 */
    @Data
    public static class Document {
        @JsonSerialize(using = ToStringSerializer.class)
        private Long id;
        private String documentType;
        private String originalFilename;
        private String contentType;
        private Long fileSize;
        private String sha256;
        private String documentStatus;
        private LocalDateTime gmtCreate;
    }
}
