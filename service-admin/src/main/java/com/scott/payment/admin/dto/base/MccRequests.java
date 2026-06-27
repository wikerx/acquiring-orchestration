package com.scott.payment.admin.dto.base;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCC 管理后台请求对象集合。
 */
public final class MccRequests {

    private MccRequests() {
    }

    /**
     * MCC 树查询请求。
     */
    @Data
    public static class MccTreeQueryRequest {
        private String keyword;
        private String mccCode;
        private String nameCn;
        private String nameEn;
        private String nodeType;
        private String riskLevel;
        private Integer status;
    }

    /**
     * MCC 分类保存请求。
     */
    @Data
    public static class MccCategorySaveRequest {
        private Long id;
        @NotBlank(message = "nodeType is required")
        private String nodeType;
        private Long parentId;
        @NotBlank(message = "categoryCode is required")
        private String categoryCode;
        @NotBlank(message = "nameCn is required")
        private String nameCn;
        @NotBlank(message = "nameEn is required")
        private String nameEn;
        private Integer sortNo;
        private Integer status;
        private String remark;
    }

    /**
     * MCC 编码保存请求。
     */
    @Data
    public static class MccCodeSaveRequest {
        private Long id;
        @NotBlank(message = "mccCode is required")
        @Pattern(regexp = "^[0-9]{4}$", message = "mccCode must be 4 digits")
        private String mccCode;
        @NotBlank(message = "nameCn is required")
        private String nameCn;
        @NotBlank(message = "nameEn is required")
        private String nameEn;
        @NotNull(message = "level1Id is required")
        private Long level1Id;
        @NotNull(message = "level2Id is required")
        private Long level2Id;
        @NotBlank(message = "mccType is required")
        private String mccType;
        @NotBlank(message = "riskLevel is required")
        private String riskLevel;
        private String deliveryApplicability;
        private String source;
        private String versionNo;
        private LocalDateTime effectiveTime;
        private LocalDateTime expireTime;
        private Integer sortNo;
        private Integer status;
        private String remark;
    }

    /**
     * MCC 风险策略分页查询请求。
     */
    @Data
    public static class MccRiskPolicyQueryRequest {
        private int pageNo = 1;
        private int pageSize = 10;
        private String mccCode;
        private String mccName;
        private String cardScheme;
        private String channelScope;
        private String channelCode;
        private String countryScope;
        private String countryCode;
        private String riskLevel;
        private Integer allowOnboarding;
        private Integer allowAcquiring;
        private Integer requireEnhancedReview;
        private Integer status;

        public int safePageNo() {
            return pageNo <= 0 ? 1 : pageNo;
        }

        public int safePageSize() {
            if (pageSize <= 0) {
                return 10;
            }
            return Math.min(pageSize, 200);
        }
    }

    /**
     * MCC 风险策略保存请求。
     */
    @Data
    public static class MccRiskPolicySaveRequest {
        private Long id;
        @NotBlank(message = "mccCode is required")
        @Pattern(regexp = "^[0-9]{4}$", message = "mccCode must be 4 digits")
        private String mccCode;
        private List<String> cardSchemes;
        private Boolean selectAllCardSchemes;
        @NotBlank(message = "channelScope is required")
        private String channelScope;
        private String channelCode;
        @NotBlank(message = "countryScope is required")
        private String countryScope;
        private String countryCode;
        @NotBlank(message = "riskLevel is required")
        private String riskLevel;
        private Integer allowOnboarding;
        private Integer allowAcquiring;
        private Integer requireEnhancedReview;
        private Integer status;
        private String remark;
    }

    /**
     * MCC 状态更新请求。
     */
    @Data
    public static class MccStatusUpdateRequest {
        private String nodeType;
        @NotNull(message = "id is required")
        private Long id;
        @NotNull(message = "status is required")
        private Integer status;
    }

    /**
     * MCC 删除请求。
     */
    @Data
    public static class MccDeleteRequest {
        private String nodeType;
        @NotNull(message = "id is required")
        private Long id;
    }

    /**
     * MCC 详情请求。
     */
    @Data
    public static class MccIdRequest {
        @NotNull(message = "id is required")
        private Long id;
    }
}
