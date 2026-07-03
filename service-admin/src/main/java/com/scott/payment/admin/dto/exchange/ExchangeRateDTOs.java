package com.scott.payment.admin.dto.exchange;

import com.scott.payment.component.core.model.PageRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理后台汇率管理请求和响应 DTO 集合。
 *
 * <p>用于汇率源、原始汇率、规则、业务汇率和快照页面，避免数据库实体直接暴露给前端。</p>
 */
public final class ExchangeRateDTOs {

    private ExchangeRateDTOs() {
    }

    /**
     * 管理端通用状态切换请求。
     */
    @Data
    public static class StatusRequest {
        @NotNull(message = "status is required")
        private Integer status;
    }

    /**
     * 原始汇率作废请求。
     */
    @Data
    public static class VoidRequest {
        @NotBlank(message = "voidReason is required")
        private String voidReason;
    }

    /**
     * 业务汇率生成请求。
     */
    @Data
    public static class GenerateBusinessRateRequest {
        /** 原始汇率记录 ID，用于选择汇率源报价。 */
        @NotNull(message = "rawRateId is required")
        private Long rawRateId;
        /** 汇率规则 ID，用于决定取值字段、调整方式和舍入规则。 */
        @NotNull(message = "ruleId is required")
        private Long ruleId;
        private String remark;
    }

    /**
     * 手工录入可直接使用的业务汇率请求。
     */
    @Data
    public static class BusinessRateSaveRequest {
        @NotBlank(message = "rateType is required")
        private String rateType;
        @NotBlank(message = "sourceCode is required")
        private String sourceCode;
        @NotBlank(message = "baseCurrency is required")
        private String baseCurrency;
        @NotBlank(message = "quoteCurrency is required")
        private String quoteCurrency;
        /** 管理端手工录入的原始汇率，后端继续以 BigDecimal 解析，避免前端浮点数精度损失。 */
        @NotNull(message = "originalRate is required")
        private BigDecimal originalRate;
        /** 最终可用业务汇率，保存后可直接被交易或结算链路读取。 */
        @NotNull(message = "finalRate is required")
        private BigDecimal finalRate;
        @NotNull(message = "effectiveTime is required")
        private LocalDateTime effectiveTime;
        private String rateStatus;
        private String remark;
    }

    /**
     * 批量手工录入业务汇率请求。
     */
    @Data
    public static class BusinessRateBatchSaveRequest {
        @Valid
        @Size(min = 1, max = 500, message = "items size must be between 1 and 500")
        private List<BusinessRateSaveRequest> items;
    }

    /**
     * 汇率源分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class SourceQuery extends PageRequest {
        private String keyword;
        private String sourceType;
        private Integer sourceStatus;
    }

    /**
     * 汇率源新增或修改请求。
     */
    @Data
    public static class SourceSaveRequest {
        @NotBlank(message = "sourceCode is required")
        private String sourceCode;
        @NotBlank(message = "sourceName is required")
        private String sourceName;
        @NotBlank(message = "sourceType is required")
        private String sourceType;
        private String requestUrl;
        private Integer defaultSource;
        private Integer priority;
        private Integer timeoutSeconds;
        @NotNull(message = "sourceStatus is required")
        private Integer sourceStatus;
        private String remark;
    }

    /**
     * 汇率源管理页面响应。
     */
    @Data
    public static class SourceResponse {
        private Long id;
        private String sourceCode;
        private String sourceName;
        private String sourceType;
        private String requestUrl;
        private Integer defaultSource;
        private Integer priority;
        private Integer timeoutSeconds;
        private Integer sourceStatus;
        private LocalDateTime lastFetchTime;
        private String lastFetchStatus;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /**
     * 原始汇率分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RawRateQuery extends PageRequest {
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        private String rateStatus;
        private String createMethod;
        private LocalDateTime publishStartTime;
        private LocalDateTime publishEndTime;
        private LocalDateTime fetchStartTime;
        private LocalDateTime fetchEndTime;
    }

    /**
     * 手工录入原始汇率请求。
     */
    @Data
    public static class RawRateSaveRequest {
        @NotBlank(message = "sourceCode is required")
        private String sourceCode;
        @NotBlank(message = "baseCurrency is required")
        private String baseCurrency;
        @NotBlank(message = "quoteCurrency is required")
        private String quoteCurrency;
        /** 现钞买入价，保留高精度 BigDecimal，禁止使用 double/float。 */
        private BigDecimal cashBuyRate;
        /** 现钞卖出价，保留高精度 BigDecimal，禁止使用 double/float。 */
        private BigDecimal cashSellRate;
        /** 现汇买入价，保留高精度 BigDecimal，禁止使用 double/float。 */
        private BigDecimal spotBuyRate;
        /** 现汇卖出价，保留高精度 BigDecimal，禁止使用 double/float。 */
        private BigDecimal spotSellRate;
        /** 中间折算价，保留高精度 BigDecimal，禁止使用 double/float。 */
        private BigDecimal middleRate;
        /** 外部汇率源发布时间，数据库使用 DATETIME(3) 保存。 */
        @NotNull(message = "publishTime is required")
        private LocalDateTime publishTime;
        /** 原始汇率生效时间；为空时使用 publishTime。 */
        private LocalDateTime effectiveTime;
        private String batchNo;
        private String remark;
    }

    /**
     * 原始汇率页面响应。
     */
    @Data
    public static class RawRateResponse {
        private Long id;
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        private BigDecimal cashBuyRate;
        private BigDecimal cashSellRate;
        private BigDecimal spotBuyRate;
        private BigDecimal spotSellRate;
        private BigDecimal middleRate;
        private LocalDateTime publishTime;
        private LocalDateTime fetchTime;
        private LocalDateTime effectiveTime;
        private String createMethod;
        private String batchNo;
        private String rateStatus;
        private String voidReason;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /**
     * 汇率规则分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RuleQuery extends PageRequest {
        private String rateType;
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        private String adjustDirection;
        private String adjustMethod;
        private Integer ruleStatus;
    }

    /**
     * 汇率规则新增或修改请求。
     */
    @Data
    public static class RuleSaveRequest {
        @NotBlank(message = "rateType is required")
        private String rateType;
        @NotBlank(message = "sourceCode is required")
        private String sourceCode;
        @NotBlank(message = "baseCurrency is required")
        private String baseCurrency;
        @NotBlank(message = "quoteCurrency is required")
        private String quoteCurrency;
        @NotBlank(message = "rateField is required")
        private String rateField;
        @NotBlank(message = "adjustDirection is required")
        private String adjustDirection;
        @NotBlank(message = "adjustMethod is required")
        private String adjustMethod;
        /** 调整值：BP 表示基点，PERCENT 表示百分比，统一使用 BigDecimal。 */
        @NotNull(message = "adjustValue is required")
        private BigDecimal adjustValue;
        /** 最终业务汇率小数位，当前限制为 2 到 12 位。 */
        @NotNull(message = "decimalScale is required")
        private Integer decimalScale;
        @NotBlank(message = "roundingMode is required")
        private String roundingMode;
        private Integer priority;
        private LocalDateTime effectiveStartTime;
        private LocalDateTime effectiveEndTime;
        @NotNull(message = "ruleStatus is required")
        private Integer ruleStatus;
        private String remark;
    }

    /**
     * 汇率规则页面响应。
     */
    @Data
    public static class RuleResponse {
        private Long id;
        private String rateType;
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        private String rateField;
        private String adjustDirection;
        private String adjustMethod;
        private BigDecimal adjustValue;
        private Integer decimalScale;
        private String roundingMode;
        private Integer priority;
        private LocalDateTime effectiveStartTime;
        private LocalDateTime effectiveEndTime;
        private Integer ruleStatus;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /**
     * 业务汇率分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class BusinessRateQuery extends PageRequest {
        private String rateType;
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        private String rateStatus;
        private String generateMethod;
    }

    /**
     * 业务汇率页面响应。
     */
    @Data
    public static class BusinessRateResponse {
        private Long id;
        private String rateType;
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        private Long rawRateId;
        private Long ruleId;
        /** 规则选取的原始报价。 */
        private BigDecimal originalRate;
        /** 最终业务汇率，已按规则调整并舍入。 */
        private BigDecimal finalRate;
        private String adjustDescription;
        private LocalDateTime effectiveTime;
        private LocalDateTime expireTime;
        private String generateMethod;
        private String rateStatus;
        private String remark;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /**
     * 汇率使用快照分页查询条件。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class UsageSnapshotQuery extends PageRequest {
        private String rateType;
        private String usageScene;
        private String businessType;
        private String businessNo;
        private String baseCurrency;
        private String quoteCurrency;
        private LocalDateTime appliedStartTime;
        private LocalDateTime appliedEndTime;
    }

    /**
     * 汇率使用快照页面响应。
     */
    @Data
    public static class UsageSnapshotResponse {
        private Long id;
        private String rateType;
        private String usageScene;
        private String businessType;
        private String businessNo;
        private String baseCurrency;
        private String quoteCurrency;
        /** 业务链路实际使用的汇率，必须与交易、清分或结算快照一致。 */
        private BigDecimal usedRate;
        private Long businessRateId;
        private Long rawRateId;
        private Long ruleId;
        private String calculationDescription;
        private LocalDateTime appliedTime;
        private LocalDateTime createTime;
    }
}
