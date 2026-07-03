package com.scott.payment.admin.dto.channel;

import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 渠道管理请求和响应 DTO 集合。
 *
 * <p>用于管理端渠道资料维护接口，避免数据库实体直接暴露给前端。</p>
 */
public final class ChannelDTOs {

    private ChannelDTOs() {
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class ChannelInfoQuery extends PageRequest {
        private String keyword;
        private Integer channelStatus;
        private Integer supportAcquiring;
        private Integer supportPayout;
        private Integer support3ds;
    }

    @Data
    public static class ChannelInfoSaveRequest {
        @NotBlank(message = "channelCode is required")
        private String channelCode;
        @NotBlank(message = "channelCnName is required")
        private String channelCnName;
        @NotBlank(message = "channelEnName is required")
        private String channelEnName;
        @NotNull(message = "channelStatus is required")
        private Integer channelStatus;
        @NotNull(message = "supportAcquiring is required")
        private Integer supportAcquiring;
        @NotNull(message = "supportPayout is required")
        private Integer supportPayout;
        @NotNull(message = "support3ds is required")
        private Integer support3ds;
        private String defaultRequestUrl;
        private String defaultInteractionMode;
        private Integer sortOrder;
        private String remark;
    }

    @Data
    public static class ChannelInfoResponse {
        private Long id;
        private String channelCode;
        private String channelCnName;
        private String channelEnName;
        private Integer channelStatus;
        private Integer supportAcquiring;
        private Integer supportPayout;
        private Integer support3ds;
        private String defaultRequestUrl;
        private String defaultInteractionMode;
        private Integer sortOrder;
        private String remark;
        private List<String> acquiringPaymentMethods = new ArrayList<>();
        private List<String> payoutPaymentMethods = new ArrayList<>();
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class CapabilityQuery extends PageRequest {
        private Long channelId;
        private String businessType;
        private String paymentMethod;
        private String transactionType;
        private String currencyCode;
        private String cardBrand;
        private Integer capabilityStatus;
    }

    @Data
    public static class CapabilitySaveRequest {
        @NotNull(message = "channelId is required")
        private Long channelId;
        @NotBlank(message = "businessType is required")
        private String businessType;
        @NotBlank(message = "paymentMethod is required")
        private String paymentMethod;
        private String transactionType;
        private List<String> currencyCodes = new ArrayList<>();
        private List<String> cardBrands = new ArrayList<>();
        private Integer support3ds;
        private Integer supportIncrementalAuthorization;
        @NotNull(message = "capabilityStatus is required")
        private Integer capabilityStatus;
        private Integer sortOrder;
        private String remark;
    }

    @Data
    public static class CapabilityResponse {
        private Long id;
        private Long channelId;
        private String channelCode;
        private String channelName;
        private String businessType;
        private String paymentMethod;
        private String transactionType;
        private List<String> currencyCodes = new ArrayList<>();
        private List<String> cardBrands = new ArrayList<>();
        private Integer support3ds;
        private Integer supportIncrementalAuthorization;
        private Integer capabilityStatus;
        private Integer sortOrder;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class LimitQuery extends PageRequest {
        private Long channelId;
        private String businessType;
        private String paymentMethod;
        private String transactionType;
        private String cardBrand;
        private String limitType;
        private Integer ruleStatus;
    }

    @Data
    public static class LimitSaveRequest {
        @NotNull(message = "channelId is required")
        private Long channelId;
        @NotBlank(message = "businessType is required")
        private String businessType;
        private String paymentMethod;
        private String transactionType;
        private String cardBrand;
        @NotBlank(message = "limitType is required")
        private String limitType;
        @NotNull(message = "limitAmount is required")
        private BigDecimal limitAmount;
        private LocalDateTime effectiveStartTime;
        private LocalDateTime effectiveEndTime;
        @NotNull(message = "ruleStatus is required")
        private Integer ruleStatus;
        private String remark;
    }

    @Data
    public static class LimitResponse {
        private Long id;
        private Long channelId;
        private String channelCode;
        private String channelName;
        private String businessType;
        private String paymentMethod;
        private String transactionType;
        private String cardBrand;
        private String limitType;
        private String limitCurrency;
        private BigDecimal limitAmount;
        private LocalDateTime effectiveStartTime;
        private LocalDateTime effectiveEndTime;
        private Integer ruleStatus;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AccessQuery extends PageRequest {
        private Long channelId;
        private String envMode;
        private String interactionMode;
        private Integer configStatus;
    }

    @Data
    public static class AccessSaveRequest {
        @NotNull(message = "channelId is required")
        private Long channelId;
        @NotBlank(message = "envMode is required")
        private String envMode;
        @NotBlank(message = "baseUrl is required")
        private String baseUrl;
        private String callbackUrl;
        @NotBlank(message = "interactionMode is required")
        private String interactionMode;
        private String channelMerchantNo;
        private String apiKey;
        private String apiSecret;
        private String clientCertPath;
        private String clientCertPassword;
        private String serverCertPath;
        private String extraConfigJson;
        @NotNull(message = "configStatus is required")
        private Integer configStatus;
        private String remark;
    }

    @Data
    public static class AccessResponse {
        private Long id;
        private Long channelId;
        private String channelCode;
        private String channelName;
        private String envMode;
        private String baseUrl;
        private String callbackUrl;
        private String interactionMode;
        private String channelMerchantNo;
        private String apiKeyMasked;
        private String apiSecretMasked;
        private String clientCertPath;
        private String clientCertPasswordMasked;
        private String serverCertPath;
        private String extraConfigJson;
        private Integer configStatus;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class StatusRequest {
        @NotNull(message = "status is required")
        private Integer status;
    }

    @Data
    public static class ChannelOption {
        private Long id;
        private String channelCode;
        private String channelName;
        private Integer channelStatus;
        private Integer supportAcquiring;
        private Integer supportPayout;
    }
}
