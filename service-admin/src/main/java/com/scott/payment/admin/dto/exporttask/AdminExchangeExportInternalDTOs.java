package com.scott.payment.admin.dto.exporttask;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data 分页读取管理端汇率事实导出源的内部契约。
 *
 * <p>本契约只暴露既有同步导出的字段，不包含汇率生成、规则计算或方向换算能力。</p>
 */
public final class AdminExchangeExportInternalDTOs {

    /** 阻止内部契约容器被实例化。 */
    private AdminExchangeExportInternalDTOs() {
    }

    /** 原始汇率冻结查询条件。 */
    @Data
    public static class RawRateQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        private String rateStatus;
        private String createMethod;
        private LocalDateTime publishStartTime;
        private LocalDateTime publishEndTime;
        private LocalDateTime fetchStartTime;
        private LocalDateTime fetchEndTime;
        private LocalDateTime effectiveStartTime;
        private LocalDateTime effectiveEndTime;
    }

    /** 业务汇率冻结查询条件。 */
    @Data
    public static class BusinessRateQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String rateType;
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        private String rateStatus;
        private String generateMethod;
        private LocalDateTime effectiveStartTime;
        private LocalDateTime effectiveEndTime;
        private LocalDateTime createStartTime;
        private LocalDateTime createEndTime;
    }

    /** 汇率使用快照冻结查询条件。 */
    @Data
    public static class UsageSnapshotQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String rateType;
        private String usageScene;
        private String businessType;
        private String businessNo;
        private String baseCurrency;
        private String quoteCurrency;
        private LocalDateTime appliedStartTime;
        private LocalDateTime appliedEndTime;
    }

    /** Data 分页读取原始汇率事实的内部请求。 */
    @Data
    public static class RawRatePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private RawRateQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /** Data 分页读取业务汇率事实的内部请求。 */
    @Data
    public static class BusinessRatePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private BusinessRateQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /** Data 分页读取汇率使用快照事实的内部请求。 */
    @Data
    public static class UsageSnapshotPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private UsageSnapshotQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /** 一页原始汇率事实安全投影。 */
    @Data
    public static class RawRatePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<RawRateRow> records = new ArrayList<>();
    }

    /** 一页业务汇率事实安全投影。 */
    @Data
    public static class BusinessRatePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<BusinessRateRow> records = new ArrayList<>();
    }

    /** 一页汇率使用快照事实安全投影。 */
    @Data
    public static class UsageSnapshotPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<UsageSnapshotRow> records = new ArrayList<>();
    }

    /** 原始汇率导出行，报价字段保持数据库十进制事实。 */
    @Data
    public static class RawRateRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        private BigDecimal spotBuyRate;
        private BigDecimal spotSellRate;
        private BigDecimal cashBuyRate;
        private BigDecimal cashSellRate;
        private BigDecimal middleRate;
        private LocalDateTime publishTime;
        private LocalDateTime fetchTime;
        private LocalDateTime effectiveTime;
        private String createMethod;
        private String batchNo;
        private String rateStatus;
        private String voidReason;
        private LocalDateTime createTime;
    }

    /** 业务汇率导出行，原始汇率和最终汇率均保持原方向与精度。 */
    @Data
    public static class BusinessRateRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String rateType;
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        private BigDecimal originalRate;
        private BigDecimal finalRate;
        private LocalDateTime effectiveTime;
        private LocalDateTime expireTime;
        private String generateMethod;
        private String rateStatus;
        private Long rawRateId;
        private Long ruleId;
        private String adjustDescription;
        private String remark;
        private LocalDateTime createTime;
    }

    /** 汇率使用快照导出行，已使用汇率保持业务写入时的原值。 */
    @Data
    public static class UsageSnapshotRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String rateType;
        private String usageEventId;
        private String producerService;
        private String usageScene;
        private String businessType;
        private String businessNo;
        private String baseCurrency;
        private String quoteCurrency;
        private BigDecimal usedRate;
        private Long rawRateId;
        private Long ruleId;
        private Long businessRateId;
        private String rateSource;
        private String sourceQuoteDirection;
        private LocalDateTime rateEffectiveTime;
        private LocalDateTime appliedTime;
        private String calculationDescription;
        private LocalDateTime createTime;
    }

    /** 汇率源导出筛选条件。 */
    @Data
    public static class SourceQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String sourceType;
        private Integer sourceStatus;
    }

    /** 汇率规则导出筛选条件。 */
    @Data
    public static class RuleQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String rateType;
        private String sourceCode;
        private String baseCurrency;
        private String quoteCurrency;
        private String adjustDirection;
        private String adjustMethod;
        private Integer ruleStatus;
    }

    /** 汇率波动导出筛选条件。 */
    @Data
    public static class VolatilityQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String rateType;
        private List<CurrencyPair> currencyPairs = new ArrayList<>();
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    /** 汇率波动固定方向币种对。 */
    @Data
    public static class CurrencyPair implements Serializable {
        private static final long serialVersionUID = 1L;
        private String baseCurrency;
        private String quoteCurrency;
    }

    /** 汇率预警规则导出筛选条件。 */
    @Data
    public static class AlertRuleQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String keyword;
        private String rateType;
        private String baseCurrency;
        private String quoteCurrency;
        private Integer ruleStatus;
        private Integer systemMandatory;
    }

    /** 汇率预警事件导出筛选条件。 */
    @Data
    public static class AlertEventQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String eventNo;
        private String rateType;
        private String baseCurrency;
        private String quoteCurrency;
        private String eventStatus;
        private String acknowledgedBy;
        private LocalDateTime triggerStartTime;
        private LocalDateTime triggerEndTime;
    }

    /** 汇率源内部分页请求。 */
    @Data
    public static class SourcePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private SourceQuery query;
        @Min(1)
        private int pageNo;
        @Min(1)
        @Max(1000)
        private int pageSize;
    }

    /** 汇率规则内部分页请求。 */
    @Data
    public static class RulePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private RuleQuery query;
        @Min(1)
        private int pageNo;
        @Min(1)
        @Max(1000)
        private int pageSize;
    }

    /** 汇率波动内部分页请求。 */
    @Data
    public static class VolatilityPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private VolatilityQuery query;
        @Min(1)
        private int pageNo;
        @Min(1)
        @Max(1000)
        private int pageSize;
    }

    /** 汇率预警规则内部分页请求。 */
    @Data
    public static class AlertRulePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private AlertRuleQuery query;
        @Min(1)
        private int pageNo;
        @Min(1)
        @Max(1000)
        private int pageSize;
    }

    /** 汇率预警事件内部分页请求。 */
    @Data
    public static class AlertEventPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Valid
        @NotNull
        private AlertEventQuery query;
        @Min(1)
        private int pageNo;
        @Min(1)
        @Max(1000)
        private int pageSize;
    }

    /** 汇率源分页响应。 */
    @Data
    public static class SourcePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<SourceRow> records = new ArrayList<>();
    }

    /** 汇率规则分页响应。 */
    @Data
    public static class RulePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<RuleRow> records = new ArrayList<>();
    }

    /** 汇率波动分页响应。 */
    @Data
    public static class VolatilityPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<VolatilityRow> records = new ArrayList<>();
    }

    /** 汇率预警规则分页响应。 */
    @Data
    public static class AlertRulePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<AlertRuleRow> records = new ArrayList<>();
    }

    /** 汇率预警事件分页响应。 */
    @Data
    public static class AlertEventPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<AlertEventRow> records = new ArrayList<>();
    }

    /** 汇率源导出安全投影。 */
    @Data
    public static class SourceRow implements Serializable {
        private static final long serialVersionUID = 1L;
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

    /** 汇率规则导出安全投影。 */
    @Data
    public static class RuleRow implements Serializable {
        private static final long serialVersionUID = 1L;
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
        private LocalDateTime updateTime;
    }

    /** 汇率波动导出投影，数值字符串保持权威服务返回精度。 */
    @Data
    public static class VolatilityRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String rateType;
        private String baseCurrency;
        private String quoteCurrency;
        private String latestRate;
        private String minimumRate;
        private String maximumRate;
        private String volatilityRate;
        private Integer sampleCount;
        private LocalDateTime latestEffectiveTime;
        private String alertStatus;
    }

    /** 汇率预警规则导出投影。 */
    @Data
    public static class AlertRuleRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String ruleCode;
        private String ruleName;
        private String rateType;
        private String baseCurrency;
        private String quoteCurrency;
        private Integer windowMinutes;
        private String triggerThreshold;
        private String recoveryThreshold;
        private Integer recoveryConsecutiveCount;
        private Integer minimumSampleCount;
        private String alertLevel;
        private Integer systemMandatory;
        private Integer ruleStatus;
        private String remark;
        private LocalDateTime updateTime;
    }

    /** 汇率预警事件导出投影，仅包含既有同步导出的只读证据。 */
    @Data
    public static class AlertEventRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String eventNo;
        private String ruleCode;
        private String rateType;
        private String baseCurrency;
        private String quoteCurrency;
        private String alertLevel;
        private String eventStatus;
        private String volatilityRate;
        private String maximumVolatilityRate;
        private String triggerThreshold;
        private String minimumRate;
        private String minimumRateSource;
        private String maximumRate;
        private String maximumRateSource;
        private String latestRate;
        private String latestRateSource;
        private Integer sampleCount;
        private LocalDateTime firstTriggerTime;
        private LocalDateTime lastDetectedTime;
        private String acknowledgedBy;
        private LocalDateTime resolvedTime;
    }
}
