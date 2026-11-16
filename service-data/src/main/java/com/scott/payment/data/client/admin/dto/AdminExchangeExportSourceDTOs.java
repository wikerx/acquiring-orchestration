package com.scott.payment.data.client.admin.dto;

import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeBusinessRateQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeRawRateQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeUsageSnapshotQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeSourceQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeRuleQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeVolatilityQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeAlertRuleQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.ExchangeAlertEventQuery;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * service-data 调用管理端汇率事实导出来源的内部模型。
 *
 * <p>所有十进制汇率字段使用 BigDecimal，禁止在跨服务投影中降级为浮点数。</p>
 */
public final class AdminExchangeExportSourceDTOs {

    /**
     * 阻止仅用于归集内部传输模型的容器类被实例化。
     */
    private AdminExchangeExportSourceDTOs() {
    }

    /**
     * 承载原始汇率事实导出的操作人、冻结查询和服务端分页参数。
     */
    @Data
    public static class RawRatePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private ExchangeRawRateQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 承载业务汇率事实导出的操作人、冻结查询和服务端分页参数。
     */
    @Data
    public static class BusinessRatePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private ExchangeBusinessRateQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 承载汇率使用快照导出的操作人、冻结查询和服务端分页参数。
     */
    @Data
    public static class UsageSnapshotPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private ExchangeUsageSnapshotQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 承载汇率源配置导出的操作人、冻结查询和服务端分页参数。
     */
    @Data
    public static class SourcePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private ExchangeSourceQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 承载汇率规则配置导出的操作人、冻结查询和服务端分页参数。
     */
    @Data
    public static class RulePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private ExchangeRuleQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 承载汇率波动摘要导出的操作人、冻结查询和服务端分页参数。
     */
    @Data
    public static class VolatilityPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private ExchangeVolatilityQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 承载汇率预警规则导出的操作人、冻结查询和服务端分页参数。
     */
    @Data
    public static class AlertRulePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private ExchangeAlertRuleQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 承载汇率预警事件导出的操作人、冻结查询和服务端分页参数。
     */
    @Data
    public static class AlertEventPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private ExchangeAlertEventQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 承载一页原始汇率事实安全投影及其分页元数据。
     */
    @Data
    public static class RawRatePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<RawRateRow> records = new ArrayList<>();
    }

    /**
     * 承载一页业务汇率事实安全投影及其分页元数据。
     */
    @Data
    public static class BusinessRatePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<BusinessRateRow> records = new ArrayList<>();
    }

    /**
     * 承载一页汇率使用快照安全投影及其分页元数据。
     */
    @Data
    public static class UsageSnapshotPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<UsageSnapshotRow> records = new ArrayList<>();
    }

    /**
     * 承载一页汇率源配置安全投影及其分页元数据。
     */
    @Data
    public static class SourcePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<SourceRow> records = new ArrayList<>();
    }

    /**
     * 承载一页汇率规则配置安全投影及其分页元数据。
     */
    @Data
    public static class RulePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<RuleRow> records = new ArrayList<>();
    }

    /**
     * 承载一页权威汇率波动摘要及其分页元数据。
     */
    @Data
    public static class VolatilityPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<VolatilityRow> records = new ArrayList<>();
    }

    /**
     * 承载一页汇率预警规则安全投影及其分页元数据。
     */
    @Data
    public static class AlertRulePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<AlertRuleRow> records = new ArrayList<>();
    }

    /**
     * 承载一页汇率预警事件冻结证据及其分页元数据。
     */
    @Data
    public static class AlertEventPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<AlertEventRow> records = new ArrayList<>();
    }

    /**
     * 承载原始汇率导出源行，跨服务传输时保持报价精度和方向不变。
     */
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

    /**
     * 承载业务汇率导出源行，原始汇率和最终汇率均保持数据库事实值。
     */
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

    /**
     * 承载汇率使用快照导出源行，已使用汇率保持业务固化时的原值。
     */
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

    /**
     * 承载管理端允许导出的汇率源配置字段。
     */
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

    /**
     * 承载管理端允许导出的汇率规则字段，调整值保持十进制精度。
     */
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

    /**
     * 承载 Admin 权威算法产出的汇率波动摘要，不在 Data 侧重新计算。
     */
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

    /**
     * 承载汇率预警规则导出字段，阈值字符串保持业务服务返回值。
     */
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

    /**
     * 承载汇率预警事件冻结证据和处理审计字段，不改变事件状态。
     */
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
