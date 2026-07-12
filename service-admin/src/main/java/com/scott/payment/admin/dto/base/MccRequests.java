package com.scott.payment.admin.dto.base;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MccRequests
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 基础数据Mcc Requests，位于 service-admin 的接口传输层，用于说明职责边界、数据语义和关键业务约束。
 * @status : create
 */
public final class MccRequests {

    private MccRequests() {
    }

    /**
     * MCC 树查询请求。
     */
    @Data
    public static class MccTreeQueryRequest {
        /**
         * 基础数据敏感或密钥相关字段，日志和接口展示必须脱敏，必要时仅保存密文。
         */
        private String keyword;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String mccCode;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String nameCn;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String nameEn;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String nodeType;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String riskLevel;
        /**
         * 基础数据状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
    }

    /**
     * MCC 分类保存请求。
     */
    @Data
    public static class MccCategorySaveRequest {
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        @NotBlank(message = "nodeType is required")
        private String nodeType;
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long parentId;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        @NotBlank(message = "categoryCode is required")
        private String categoryCode;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "nameCn is required")
        private String nameCn;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "nameEn is required")
        private String nameEn;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private Integer sortNo;
        /**
         * 基础数据状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 基础数据备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    /**
     * MCC 编码保存请求。
     */
    @Data
    public static class MccCodeSaveRequest {
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        @NotBlank(message = "mccCode is required")
        @Pattern(regexp = "^[0-9]{4}$", message = "mccCode must be 4 digits")
        private String mccCode;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "nameCn is required")
        private String nameCn;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "nameEn is required")
        private String nameEn;
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @NotNull(message = "level1Id is required")
        private Long level1Id;
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @NotNull(message = "level2Id is required")
        private Long level2Id;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "mccType is required")
        private String mccType;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "riskLevel is required")
        private String riskLevel;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String deliveryApplicability;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String source;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String versionNo;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime effectiveTime;
        /**
         * 基础数据时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
         */
        private LocalDateTime expireTime;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private Integer sortNo;
        /**
         * 基础数据状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 基础数据备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    /**
     * MCC 风险策略分页查询请求。
     */
    @Data
    public static class MccRiskPolicyQueryRequest {
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private int pageNo = 1;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private int pageSize = 10;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String mccCode;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String mccName;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String cardScheme;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String channelScope;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String channelCode;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String countryScope;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String countryCode;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private String riskLevel;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer allowOnboarding;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer allowAcquiring;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer requireEnhancedReview;
        /**
         * 基础数据状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;

        /**
         * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
         * @return 处理后的业务结果或页面展示数据。
         */

        public int safePageNo() {
            return pageNo <= 0 ? 1 : pageNo;
        }

        /**
         * 执行基础数据相关处理，保持当前层级的职责边界和返回语义。
         * @return 处理后的业务结果或页面展示数据。
         */

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
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        private Long id;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        @NotBlank(message = "mccCode is required")
        @Pattern(regexp = "^[0-9]{4}$", message = "mccCode must be 4 digits")
        private String mccCode;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private List<String> cardSchemes;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Boolean selectAllCardSchemes;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "channelScope is required")
        private String channelScope;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String channelCode;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "countryScope is required")
        private String countryScope;
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String countryCode;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        @NotBlank(message = "riskLevel is required")
        private String riskLevel;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer allowOnboarding;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer allowAcquiring;
        /**
         * 基础数据业务字段，承载页面展示、接口传输或持久化所需的数据语义。
         */
        private Integer requireEnhancedReview;
        /**
         * 基础数据状态字段，取值需与数据字典或枚举约定保持一致。
         */
        private Integer status;
        /**
         * 基础数据备注字段，用于记录人工说明，不参与核心状态流转。
         */
        private String remark;
    }

    /**
     * MCC 状态更新请求。
     */
    @Data
    public static class MccStatusUpdateRequest {
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String nodeType;
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @NotNull(message = "id is required")
        private Long id;
        /**
         * 基础数据状态字段，取值需与数据字典或枚举约定保持一致。
         */
        @NotNull(message = "status is required")
        private Integer status;
    }

    /**
     * MCC 删除请求。
     */
    @Data
    public static class MccDeleteRequest {
        /**
         * 基础数据编码或编号字段，用于业务识别、查询和幂等关联。
         */
        private String nodeType;
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @NotNull(message = "id is required")
        private Long id;
    }

    /**
     * MCC 详情请求。
     */
    @Data
    public static class MccIdRequest {
        /**
         * 基础数据标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
         */
        @NotNull(message = "id is required")
        private Long id;
    }
}
