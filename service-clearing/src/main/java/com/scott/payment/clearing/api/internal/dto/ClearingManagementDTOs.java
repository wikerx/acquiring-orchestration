package com.scott.payment.clearing.api.internal.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/** 清分内部查询与管理接口模型，不包含持卡人、卡号或费用配置正文。 */
public final class ClearingManagementDTOs {

    private ClearingManagementDTOs() {
    }

    /** 动作级清分汇总。 */
    @Data
    public static class ClearingRecordSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private String financeStateId;
        private String transactionId;
        private String operationId;
        private String merchantId;
        private String sourceTransactionId;
        private String transactionType;
        private String labelCurrency;
        private String clearingStatus;
        private Integer clearingRevision;
        private Integer clearingRetryCount;
        private LocalDateTime nextRetryTime;
        private String lastFailureCode;
        private String lastFailureMessage;
        private Long feePlanId;
        private Long feePlanVersionId;
        private Integer feePlanVersionNo;
        private BigDecimal grossLabelAmount;
        private String feeEvaluationStatus;
        private String settlementStatus;
        private String settlementCurrency;
        private LocalDate settlementEligibleDate;
        private BigDecimal platformFeeAmount;
        private BigDecimal feeReversalAmount;
        private BigDecimal reserveAmount;
        private BigDecimal reserveReversalAmount;
        private LocalDate expectedReserveReleaseDate;
        private LocalDateTime transactionDateTime;
        private LocalDateTime transactionUtcTime;
        private String transactionTimeZone;
        private Integer version;
    }

    /** 交易本金或费用原子明细。 */
    @Data
    public static class ClearingTransactionLine implements Serializable {
        private static final long serialVersionUID = 1L;
        private String clearingDetailNo;
        private Integer clearingRevision;
        private Integer lineNo;
        private String itemType;
        private String feeCategory;
        private String riskServiceType;
        private String itemCode;
        private String itemName;
        private String direction;
        private String labelCurrency;
        private BigDecimal labelAmount;
        private String componentType;
        private String basisCurrency;
        private BigDecimal basisAmount;
        private BigDecimal amount;
        private String currency;
        private Integer currencyExponent;
        private BigDecimal percentageRate;
        private BigDecimal fixedAmountUsd;
        private BigDecimal minimumAmountUsd;
        private BigDecimal maximumAmountUsd;
        private String limitEvaluationStatus;
        private String appliedLimit;
        private String formulaSnapshot;
        private String recordStatus;
    }

    /** 独立保证金清分明细。 */
    @Data
    public static class ClearingReserveLine implements Serializable {
        private static final long serialVersionUID = 1L;
        private String reserveClearingDetailNo;
        private String originalTransactionId;
        private String sourceReserveDetailNo;
        private Integer clearingRevision;
        private Integer lineNo;
        private String reserveActionType;
        private String itemCode;
        private String itemName;
        private String direction;
        private String reserveCurrency;
        private Integer reserveCurrencyExponent;
        private BigDecimal basisAmount;
        private BigDecimal reserveRate;
        private BigDecimal retainedAmount;
        private BigDecimal returnedAmount;
        private BigDecimal releasedAmount;
        private BigDecimal adjustmentAmount;
        private BigDecimal remainingAmount;
        private LocalDate expectedReserveReleaseDate;
        private String formulaSnapshot;
        private String recordStatus;
    }

    /** 单笔动作清分聚合详情。 */
    @Data
    public static class ClearingRecordDetailResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private ClearingRecordSummary summary;
        private List<ClearingTransactionLine> transactionDetails = Collections.emptyList();
        private List<ClearingReserveLine> reserveDetails = Collections.emptyList();
    }

    /** 单季度清分记录游标查询。 */
    @Data
    public static class ClearingRecordSearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String transactionId;
        private String clearingStatus;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private LocalDateTime cursorTransactionDateTime;
        private Long cursorId;
        private Integer limit;
    }

    /** 清分记录游标查询响应。 */
    @Data
    public static class ClearingRecordSearchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private List<ClearingRecordSummary> records = Collections.emptyList();
        private boolean hasMore;
        private LocalDateTime nextCursorTransactionDateTime;
        private Long nextCursorId;
    }

    /** 人工重试命令；分片时间和预期版本共同防止误操作旧状态。 */
    @Data
    public static class ClearingRetryRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
        private String reason;
        private String operator;
    }

    /** 人工复核升级命令，不允许从浏览器直接指定任意目标状态。 */
    @Data
    public static class ClearingReviewRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
        private String reason;
        private String operator;
    }

    /** 未结算清分重算命令，目标必须是一个明确的不可变费用版本。 */
    @Data
    public static class ClearingRecalculateRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
        private Integer expectedClearingRevision;
        private Long targetFeePlanId;
        private Long targetFeePlanVersionId;
        private String reason;
        private String operator;
    }

    /** 清分人工命令结果，只返回状态和并发控制字段。 */
    @Data
    public static class ClearingCommandResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private String transactionId;
        private LocalDateTime transactionDateTime;
        private String action;
        private String clearingStatus;
        private Integer clearingRevision;
        private Integer version;
        private String result;
    }

    /** 保证金差额调整申请；金额币种由原保证金状态冻结，调用方不能另传币种。 */
    @Data
    public static class ReserveAdjustmentSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requestKey;
        private String reserveStateId;
        private String originalTransactionId;
        private LocalDateTime originalTransactionDateTime;
        private Long expectedReserveStateVersion;
        private String direction;
        private BigDecimal adjustmentAmount;
        private LocalDate requestedReleaseDate;
        private String reason;
        private String submitOperator;
    }

    /** 保证金调整双人复核命令；期望版本防止重复或过期决定覆盖终态。 */
    @Data
    public static class ReserveAdjustmentReviewRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long expectedRequestVersion;
        private String decision;
        private String reviewComment;
        private String reviewOperator;
    }

    /** 保证金调整申请与执行结果。 */
    @Data
    public static class ReserveAdjustmentResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private String adjustmentNo;
        private String status;
        private String transactionId;
        private Integer sourceRevision;
        private Long version;
    }

    /** 阶梯期间重放申请；范围固定为商户、不可变费用版本和 yyyyMM 月份。 */
    @Data
    public static class TierPeriodReplaySubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requestKey;
        private String merchantId;
        private Long feePlanId;
        private Long feePlanVersionId;
        private Long triggerFeeRuleId;
        private String periodKey;
        private String reason;
        private String submitOperator;
    }

    /** 阶梯期间重放双人复核命令，版本 CAS 防止过期决定覆盖状态。 */
    @Data
    public static class TierPeriodReplayReviewRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long expectedRequestVersion;
        private String decision;
        private String reviewComment;
        private String reviewOperator;
    }

    /** 阶梯期间重放申请、准备或推进结果，不返回费用配置正文。 */
    @Data
    public static class TierPeriodReplayResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private String replayNo;
        private String status;
        private Integer itemCount;
        private Integer completedCount;
        private Long version;
    }
}
