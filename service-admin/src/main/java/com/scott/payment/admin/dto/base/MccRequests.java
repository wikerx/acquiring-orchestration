package com.scott.payment.admin.dto.base;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MCC 管理请求模型集合，用于运营后台，封装该业务的本地校验、转换或运行时协作入口。
 */
public final class MccRequests {

    /**
     * 阻止仅提供静态能力的类型被实例化。
     */
    private MccRequests() {
    }

    /**
     * MCC 树查询请求。
     */
    @Data
    public static class MccTreeQueryRequest {
        /**
         * 树查询关键词，可匹配分类编码、MCC 编码或中英文名称。
         */
        private String keyword;
        private String mccCode;
        private String nameCn;
        private String nameEn;
        private String nodeType;
        /**
         * 风险等级，用于运营展示、审核和处置优先级判断。
         */
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
        /**
         * 排序号，数值越小越优先展示或匹配。
         */
        private Integer sortNo;
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
        /**
         * 风险等级，用于运营展示、审核和处置优先级判断。
         */
        @NotBlank(message = "riskLevel is required")
        private String riskLevel;
        private String deliveryApplicability;
        /**
         * 请求中的来源，用于限定本次操作的输入和校验范围。
         */
        private String source;
        /**
         * 业务版本号，用于区分同一配置或方案的不可变版本。
         */
        private String versionNo;
        /**
         * 业务配置或汇率开始生效的具体时刻。
         */
        private LocalDateTime effectiveTime;
        /**
         * 业务配置、令牌或缓存条目的失效时刻。
         */
        private LocalDateTime expireTime;
        /**
         * 排序号，数值越小越优先展示或匹配。
         */
        private Integer sortNo;
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
         * 查询页码，从 1 开始。
         */
        private int pageNo = 1;
        /**
         * 每页记录数，由接口上限约束避免无界查询。
         */
        private int pageSize = 10;
        private String mccCode;
        private String mccName;
        private String cardScheme;
        /**
         * 请求中的渠道范围，用于限定本次操作的输入和校验范围。
         */
        private String channelScope;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;
        private String countryScope;
        private String countryCode;
        /**
         * 风险等级，用于运营展示、审核和处置优先级判断。
         */
        private String riskLevel;
        private Integer allowOnboarding;
        private Integer allowAcquiring;
        private Integer requireEnhancedReview;
        private Integer status;

        /**
         * 规范化页编号，返回调用链后续步骤可直接使用的业务值。
         * @return 当前方法计算的数量、版本或状态数值
         */
        public int safePageNo() {
            return pageNo <= 0 ? 1 : pageNo;
        }

        /**
         * 规范化页大小，返回调用链后续步骤可直接使用的业务值。
         * @return 当前方法计算的数量、版本或状态数值
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
        private Long id;
        @NotBlank(message = "mccCode is required")
        @Pattern(regexp = "^[0-9]{4}$", message = "mccCode must be 4 digits")
        private String mccCode;
        private List<String> cardSchemes;
        private Boolean selectAllCardSchemes;
        /**
         * 请求中的渠道范围，用于限定本次操作的输入和校验范围。
         */
        @NotBlank(message = "channelScope is required")
        private String channelScope;
        /**
         * 渠道编码，用于定位 MPGS、WorldPay 等渠道适配实现和路由配置。
         */
        private String channelCode;
        @NotBlank(message = "countryScope is required")
        private String countryScope;
        private String countryCode;
        /**
         * 风险等级，用于运营展示、审核和处置优先级判断。
         */
        @NotBlank(message = "riskLevel is required")
        private String riskLevel;
        private Integer allowOnboarding;
        private Integer allowAcquiring;
        private Integer requireEnhancedReview;
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
