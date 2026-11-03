package com.scott.payment.admin.dto.exchange;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 汇率波动、预警规则和预警事件接口模型集合。
 *
 * <p>汇率与百分比统一使用十进制字符串，避免 JSON 数值在浏览器侧产生二进制浮点精度损失。</p>
 */
public final class ExchangeRateVolatilityDTOs {

    private ExchangeRateVolatilityDTOs() {
    }

    /** 汇率趋势查询条件；币种对为空表示查询当前汇率类型下的全部币种对。 */
    @Data
    public static class VolatilityQuery {
        @NotBlank(message = "rateType must not be blank")
        private String rateType;
        @Valid
        @Size(max = 100, message = "currencyPairs size must not exceed 100")
        private List<CurrencyPair> currencyPairs = new ArrayList<>();
        @NotNull(message = "startTime must not be null")
        private LocalDateTime startTime;
        @NotNull(message = "endTime must not be null")
        private LocalDateTime endTime;
    }

    /** 固定方向的 ISO 4217 币种对。 */
    @Data
    public static class CurrencyPair {
        @NotBlank(message = "baseCurrency must not be blank")
        @Pattern(regexp = "^[A-Z]{3}$", message = "baseCurrency must be an uppercase ISO currency")
        private String baseCurrency;
        @NotBlank(message = "quoteCurrency must not be blank")
        @Pattern(regexp = "^[A-Z]{3}$", message = "quoteCurrency must be an uppercase ISO currency")
        private String quoteCurrency;
    }

    /** 单个币种对在查询区间内的波动统计摘要。 */
    @Data
    public static class VolatilityOverview {
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

    /** 单个趋势数据点，来源仅作为审计证据返回。 */
    @Data
    public static class TrendPoint {
        private Long businessRateId;
        private String rate;
        private String sourceCode;
        private Long rawRateId;
        private Long ruleId;
        private LocalDateTime effectiveTime;
        private LocalDateTime expireTime;
    }

    /** 一个币种对的跨来源汇总趋势序列。 */
    @Data
    public static class TrendSeries {
        private String rateType;
        private String baseCurrency;
        private String quoteCurrency;
        private List<TrendPoint> points = new ArrayList<>();
    }

    /** 预警规则分页查询条件。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AlertRuleQuery extends PageQuery {
        private String keyword;
        private String rateType;
        private String baseCurrency;
        private String quoteCurrency;
        private Integer ruleStatus;
        private Integer systemMandatory;
    }

    /** 预警规则保存请求；百分比按百分数输入，例如 3 表示 3%。 */
    @Data
    public static class AlertRuleSaveRequest {
        @NotBlank(message = "ruleName must not be blank")
        @Size(max = 128, message = "ruleName length must not exceed 128")
        private String ruleName;
        @NotBlank(message = "rateType must not be blank")
        private String rateType;
        @NotBlank(message = "baseCurrency must not be blank")
        private String baseCurrency;
        @NotBlank(message = "quoteCurrency must not be blank")
        private String quoteCurrency;
        @NotNull(message = "windowMinutes must not be null")
        @Min(value = 5, message = "windowMinutes must be at least 5")
        @Max(value = 129600, message = "windowMinutes must not exceed 90 days")
        private Integer windowMinutes;
        @NotBlank(message = "triggerThreshold must not be blank")
        private String triggerThreshold;
        @NotBlank(message = "recoveryThreshold must not be blank")
        private String recoveryThreshold;
        @NotNull(message = "recoveryConsecutiveCount must not be null")
        @Min(value = 1, message = "recoveryConsecutiveCount must be at least 1")
        @Max(value = 20, message = "recoveryConsecutiveCount must not exceed 20")
        private Integer recoveryConsecutiveCount;
        @NotNull(message = "minimumSampleCount must not be null")
        @Min(value = 2, message = "minimumSampleCount must be at least 2")
        @Max(value = 10000, message = "minimumSampleCount must not exceed 10000")
        private Integer minimumSampleCount;
        @NotBlank(message = "alertLevel must not be blank")
        private String alertLevel;
        @Size(max = 512, message = "remark length must not exceed 512")
        private String remark;
    }

    /** 预警规则响应。 */
    @Data
    public static class AlertRuleResponse {
        private Long id;
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
        private String createBy;
        private LocalDateTime createTime;
        private String updateBy;
        private LocalDateTime updateTime;
    }

    /** 预警事件分页查询条件。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class AlertEventQuery extends PageQuery {
        private String eventNo;
        private String rateType;
        private String baseCurrency;
        private String quoteCurrency;
        private String eventStatus;
        private String acknowledgedBy;
        private LocalDateTime triggerStartTime;
        private LocalDateTime triggerEndTime;
    }

    /** 预警事件响应，包含触发时冻结的规则和窗口证据。 */
    @Data
    public static class AlertEventResponse {
        private Long id;
        private String eventNo;
        private Long ruleId;
        private String ruleCode;
        private String ruleName;
        private String rateType;
        private String baseCurrency;
        private String quoteCurrency;
        private String alertLevel;
        private Integer windowMinutes;
        private String triggerThreshold;
        private String recoveryThreshold;
        private LocalDateTime windowStartTime;
        private LocalDateTime windowEndTime;
        private Integer sampleCount;
        private String minimumRate;
        private String minimumRateSource;
        private Long minimumBusinessRateId;
        private String maximumRate;
        private String maximumRateSource;
        private Long maximumBusinessRateId;
        private String latestRate;
        private String latestRateSource;
        private Long latestBusinessRateId;
        private String volatilityRate;
        private String maximumVolatilityRate;
        private Integer recoveryStreak;
        private String eventStatus;
        private LocalDateTime firstTriggerTime;
        private LocalDateTime lastDetectedTime;
        private LocalDateTime resolvedTime;
        private String acknowledgedBy;
        private LocalDateTime acknowledgedTime;
        private String acknowledgeRemark;
        private String closedBy;
        private LocalDateTime closedTime;
        private String closeRemark;
        private Integer version;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    /** 人工确认或关闭事件时必须提供的审计备注和前端读取版本。 */
    @Data
    public static class AlertEventActionRequest {
        @NotBlank(message = "remark must not be blank")
        @Size(max = 512, message = "remark length must not exceed 512")
        private String remark;
        @NotNull(message = "version must not be null")
        @Min(value = 0, message = "version must not be negative")
        private Integer version;
    }

    /** service-job 发起的一轮幂等评估命令。 */
    @Data
    public static class AlertEvaluationRequest {
        @NotBlank(message = "requestKey must not be blank")
        @Size(max = 128, message = "requestKey length must not exceed 128")
        private String requestKey;
        @NotNull(message = "evaluationTime must not be null")
        private LocalDateTime evaluationTime;
    }

    /** 一轮评估的稳定结果；重复 requestKey 返回同一结果。 */
    @Data
    public static class AlertEvaluationResponse {
        private String requestKey;
        private String evaluationStatus;
        private Integer evaluatedRuleCount;
        private Integer evaluatedPairCount;
        private Integer triggeredEventCount;
        private Integer updatedEventCount;
        private Integer resolvedEventCount;
        private Integer insufficientDataCount;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
    }

    /** Excel 导入逐行结果，失败行不会被静默忽略。 */
    @Data
    public static class AlertRuleImportResult {
        private Integer totalCount;
        private Integer successCount;
        private Integer failureCount;
        private List<AlertRuleImportError> errors = new ArrayList<>();
    }

    /** Excel 导入失败行及稳定英文错误描述。 */
    @Data
    public static class AlertRuleImportError {
        private Integer rowNumber;
        private String message;
    }

    /** 统一分页参数，限制管理端单次查询规模。 */
    @Data
    public abstract static class PageQuery {
        @Min(value = 1, message = "pageNo must be greater than 0")
        private Integer pageNo = 1;
        @Min(value = 1, message = "pageSize must be greater than 0")
        @Max(value = 200, message = "pageSize must not exceed 200")
        private Integer pageSize = 20;

        public long safePageNo() {
            return pageNo == null || pageNo < 1 ? 1 : pageNo;
        }

        public long safePageSize() {
            return pageSize == null || pageSize < 1 ? 20 : Math.min(pageSize, 200);
        }
    }
}
