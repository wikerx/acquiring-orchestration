package com.scott.payment.admin.entity.exchange;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 汇率波动预警持久化实体集合。 */
public final class ExchangeRateAlertEntities {

    private ExchangeRateAlertEntities() {
    }

    /** 汇率波动预警规则；来源不属于规则维度。 */
    @Data
    @TableName("exchange_rate_alert_rule")
    public static class ExchangeRateAlertRuleDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String ruleCode;
        private String ruleName;
        private String rateType;
        private String baseCurrency;
        private String quoteCurrency;
        private Integer windowMinutes;
        private BigDecimal triggerThreshold;
        private BigDecimal recoveryThreshold;
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
        private Long deleted;
    }

    /** 汇率波动预警事件；活动键在恢复时清空，以允许下一次独立触发。 */
    @Data
    @TableName("exchange_rate_alert_event")
    public static class ExchangeRateAlertEventDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String eventNo;
        private String activeEventKey;
        private Long ruleId;
        private String ruleCode;
        private String ruleName;
        private String rateType;
        private String baseCurrency;
        private String quoteCurrency;
        private String alertLevel;
        private Integer windowMinutes;
        private BigDecimal triggerThreshold;
        private BigDecimal recoveryThreshold;
        private LocalDateTime windowStartTime;
        private LocalDateTime windowEndTime;
        private Integer sampleCount;
        private BigDecimal minimumRate;
        private String minimumRateSource;
        private Long minimumBusinessRateId;
        private BigDecimal maximumRate;
        private String maximumRateSource;
        private Long maximumBusinessRateId;
        private BigDecimal latestRate;
        private String latestRateSource;
        private Long latestBusinessRateId;
        private BigDecimal volatilityRate;
        private BigDecimal maximumVolatilityRate;
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
        private Long deleted;
    }

    /** 一轮评估的幂等执行记录。 */
    @Data
    @TableName("exchange_rate_alert_evaluation")
    public static class ExchangeRateAlertEvaluationDO {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String requestKey;
        private LocalDateTime windowEndTime;
        private String evaluationStatus;
        private Integer evaluatedRuleCount;
        private Integer evaluatedPairCount;
        private Integer triggeredEventCount;
        private Integer updatedEventCount;
        private Integer resolvedEventCount;
        private Integer insufficientDataCount;
        private String errorMessage;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private LocalDateTime createTime;
    }
}
