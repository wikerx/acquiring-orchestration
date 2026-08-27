package com.scott.payment.admin.dto.transaction;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/** Admin 清分管理接口模型；交易费用明细与保证金明细保持两个独立集合。 */
public final class AdminClearingDTOs {
    private AdminClearingDTOs() {
    }

    @Data
    public static class SearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String transactionId;
        private String clearingStatus;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private Integer pageNo;
        private Integer pageSize;
        /** 仅保留给旧版 service-clearing 内部查询客户端兼容，Admin 页面不再使用。 */
        private LocalDateTime cursorTransactionDateTime;
        /** 仅保留给旧版 service-clearing 内部查询客户端兼容，Admin 页面不再使用。 */
        private Long cursorId;
        /** 仅保留给旧版 service-clearing 内部查询客户端兼容，Admin 页面不再使用。 */
        private Integer limit;
    }

    @Data
    public static class Summary implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private String financeStateId;
        private String transactionId;
        private String operationId;
        private String merchantId;
        private String sourceTransactionId;
        private String transactionType;
        private String labelCurrency;
        /** 交易动作原始标签金额，与是否产生结算本金无关。 */
        private BigDecimal labelAmount;
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

    @Data
    public static class SearchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private List<Summary> records = Collections.emptyList();
        private boolean hasMore;
        private LocalDateTime nextCursorTransactionDateTime;
        private Long nextCursorId;
    }

    @Data
    public static class TransactionLine implements Serializable {
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

    @Data
    public static class ReserveLine implements Serializable {
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

    @Data
    public static class DetailResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private Summary summary;
        private List<TransactionLine> transactionDetails = Collections.emptyList();
        private List<ReserveLine> reserveDetails = Collections.emptyList();
    }

    /** 浏览器命令不含 operator，应用层从登录上下文补齐。 */
    @Data
    public static class ActionRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
        private String reason;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RecalculateRequest extends ActionRequest {
        private static final long serialVersionUID = 1L;
        private Integer expectedClearingRevision;
        private Long targetFeePlanId;
        private Long targetFeePlanVersionId;
    }

    /** 清分重算可选的不可变费用版本描述；数据库主键仅作为提交载荷，不要求运营人员输入。 */
    @Data
    public static class RecalculationVersionOption implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long versionId;
        private Integer versionNo;
        private String versionStatus;
    }

    /** 指定商户费用方案的清分重算选项，不返回费用规则和金额配置。 */
    @Data
    public static class RecalculationOptionsResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private Long feePlanId;
        private String planCode;
        private String planName;
        private Long currentVersionId;
        private List<RecalculationVersionOption> versions = Collections.emptyList();
    }

    /** 批量重算中的单笔身份和运营人员看到的 CAS 版本。 */
    @Data
    public static class RecalculateBatchItem implements Serializable {
        private static final long serialVersionUID = 1L;
        private String transactionId;
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
        private Integer expectedClearingRevision;
    }

    /** 同一商户、同一费用方案的一组未结算清分重算请求。 */
    @Data
    public static class RecalculateBatchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private List<RecalculateBatchItem> records = Collections.emptyList();
        private Long targetFeePlanId;
        private Long targetFeePlanVersionId;
        private String reason;
    }

    /** 批量重算逐笔结果；批量请求不伪装成跨交易原子事务。 */
    @Data
    public static class RecalculateBatchItemResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private String transactionId;
        private LocalDateTime transactionDateTime;
        private boolean success;
        private String result;
        private String message;
    }

    /** 批量重算汇总及逐笔成功、失败结果。 */
    @Data
    public static class RecalculateBatchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer requestedCount;
        private Integer successCount;
        private Integer failureCount;
        private List<RecalculateBatchItemResult> results = Collections.emptyList();
    }

    @Data
    public static class InternalActionRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private LocalDateTime transactionDateTime;
        private Integer expectedVersion;
        private String reason;
        private String operator;
    }

    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InternalRecalculateRequest extends InternalActionRequest {
        private static final long serialVersionUID = 1L;
        private Integer expectedClearingRevision;
        private Long targetFeePlanId;
        private Long targetFeePlanVersionId;
    }

    /** 浏览器提交的保证金标签币种差额申请，不允许携带提交人。 */
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
    }

    /** Admin 到清分服务的调整申请，提交人由可信登录上下文补齐。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InternalReserveAdjustmentSubmitRequest extends ReserveAdjustmentSubmitRequest {
        private static final long serialVersionUID = 1L;
        private String submitOperator;
    }

    /** 浏览器提交的保证金调整复核命令，不允许携带复核人。 */
    @Data
    public static class ReserveAdjustmentReviewRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long expectedRequestVersion;
        private String decision;
        private String reviewComment;
    }

    /** Admin 到清分服务的复核命令，复核人由可信登录上下文补齐。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InternalReserveAdjustmentReviewRequest extends ReserveAdjustmentReviewRequest {
        private static final long serialVersionUID = 1L;
        private String reviewOperator;
    }

    /** 保证金调整申请或复核结果，不包含费用配置及余额信息。 */
    @Data
    public static class ReserveAdjustmentResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private String adjustmentNo;
        private String status;
        private String transactionId;
        private Integer sourceRevision;
        private Long version;
    }

    /** 浏览器提交的阶梯期间重放申请，不允许携带提交人。 */
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
    }

    /** Admin 到清分服务的重放申请，提交人由可信登录上下文补齐。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InternalTierPeriodReplaySubmitRequest extends TierPeriodReplaySubmitRequest {
        private static final long serialVersionUID = 1L;
        private String submitOperator;
    }

    /** 浏览器提交的阶梯期间重放复核命令，不允许携带复核人。 */
    @Data
    public static class TierPeriodReplayReviewRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long expectedRequestVersion;
        private String decision;
        private String reviewComment;
    }

    /** Admin 到清分服务的重放复核命令，复核人由可信登录上下文补齐。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class InternalTierPeriodReplayReviewRequest extends TierPeriodReplayReviewRequest {
        private static final long serialVersionUID = 1L;
        private String reviewOperator;
    }

    /** 阶梯期间重放控制结果。 */
    @Data
    public static class TierPeriodReplayResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private String replayNo;
        private String status;
        private Integer itemCount;
        private Integer completedCount;
        private Long version;
    }

    @Data
    public static class CommandResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private String transactionId;
        private LocalDateTime transactionDateTime;
        private String action;
        private String clearingStatus;
        private Integer clearingRevision;
        private Integer version;
        private String result;
    }
}
