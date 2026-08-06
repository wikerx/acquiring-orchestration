package com.scott.payment.payment.service.dto.refund;

import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundManagementDTOs
 * @date : 2026-08-06 15:40
 * @email : scott_x@163.com
 * @description : 退款管理内部查询模型，统一 REFUND/VOID 查询、审批派生状态、分币种统计和详情响应契约。
 * @status : create
 */
public final class RefundManagementDTOs {

    private RefundManagementDTOs() {
    }

    /** 退款管理分页查询条件；时间范围按交易受理时间解释。 */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class RefundQuery extends PageRequest {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String refundTransactionId;
        private String sourceTransactionId;
        private String merchantOrderNo;
        private String merchantOperationNo;
        private String transactionType;
        private String refundScope;
        private String approvalStatus;
        private String transactionStatus;
        private String requestSource;
        private String channelCode;
        private String channelOrderNo;
        private String acquirerReferenceNo;
        private String paymentMethod;
        private String paymentBrand;
        private String labelCurrency;
        private String transactionCurrency;
        private BigDecimal minimumTransactionAmount;
        private BigDecimal maximumTransactionAmount;
        private String applicantId;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private LocalDateTime completeBeginTime;
        private LocalDateTime completeEndTime;
        private String queryTimeZone;
    }

    /** 退款列表记录；金额均为对应币种主单位 BigDecimal。 */
    @Data
    public static class RefundRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        private String refundTransactionId;
        private String operationId;
        private String sourceTransactionId;
        private String merchantId;
        private String merchantOrderNo;
        private String merchantOperationNo;
        private String transactionType;
        private String refundScope;
        private String requestSource;
        private String requestReason;
        private String applicantType;
        private String applicantId;
        private String applicantName;
        private String executionMode;
        private String transactionStatus;
        private String processStage;
        private String failReasonCode;
        private String failReasonMessage;
        private String labelCurrency;
        private BigDecimal labelAmount;
        private String transactionCurrency;
        private BigDecimal transactionAmount;
        private Integer currencyExponent;
        private String paymentMethod;
        private String paymentBrand;
        private String channelCode;
        private String channelOrderNo;
        private String channelTransactionId;
        private String channelResponseCode;
        private String acquirerReferenceNo;
        private String channelMatchStatus;
        private String merchantNotificationStatus;
        private LocalDateTime transactionDateTime;
        private LocalDateTime rootTransactionDateTime;
        private LocalDateTime completeTime;
        private String approvalId;
        private String approvalStatus;
        private String approvalPolicyCode;
        private String approvalOperatorId;
        private String approvalOperatorName;
        private LocalDateTime approvalTime;
        private String approvalReason;
        private LocalDateTime approvalExpireTime;
        private String executionEventId;
        private Integer approvalVersion;
    }

    /** 分币种成功退款和待处理占用金额。 */
    @Data
    public static class RefundCurrencySummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private String currency;
        private BigDecimal successfulAmount = BigDecimal.ZERO;
        private BigDecimal pendingAmount = BigDecimal.ZERO;
    }

    /** 当前完整查询条件下的退款统计。 */
    @Data
    public static class RefundSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private long totalCount;
        private long pendingApprovalCount;
        private long processingCount;
        private long successCount;
        private long failedOrRejectedCount;
        private List<RefundCurrencySummary> currencyAmounts = new ArrayList<>();
    }

    /** 退款分页和统计组合响应。 */
    @Data
    public static class RefundSearchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private PageResult<RefundRecord> page;
        private RefundSummary summary;
    }

    /** 退款详情，交易聚合详情使用非敏感内部 Map 便于 Admin 展示时间线。 */
    @Data
    public static class RefundDetailResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private RefundRecord refund;
        private Object transactionDetail;
    }

    /** SQL 状态统计投影。 */
    @Data
    public static class RefundStatusSummaryRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long totalCount;
        private Long pendingApprovalCount;
        private Long processingCount;
        private Long successCount;
        private Long failedOrRejectedCount;
    }
}
