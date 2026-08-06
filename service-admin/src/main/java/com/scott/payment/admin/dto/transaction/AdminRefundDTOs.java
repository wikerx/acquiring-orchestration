package com.scott.payment.admin.dto.transaction;

import com.scott.payment.component.core.model.PageRequest;
import com.scott.payment.component.core.model.PageResult;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminRefundDTOs
 * @date : 2026-08-06 16:00
 * @email : scott_x@163.com
 * @description : 管理端退款查询、详情和审批接口模型，保留运营排障所需渠道摘要但不包含渠道凭据或原始报文。
 * @status : create
 */
public final class AdminRefundDTOs {

    private AdminRefundDTOs() {
    }

    /** 管理端退款查询条件。 */
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

    /** 管理端退款列表记录。 */
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

    /** 分币种退款金额统计。 */
    @Data
    public static class RefundCurrencySummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private String currency;
        private BigDecimal successfulAmount = BigDecimal.ZERO;
        private BigDecimal pendingAmount = BigDecimal.ZERO;
    }

    /** 退款状态和金额统计。 */
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

    /** 退款分页与统计组合响应。 */
    @Data
    public static class RefundSearchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private PageResult<RefundRecord> page;
        private RefundSummary summary;
    }

    /** 退款详情响应。 */
    @Data
    public static class RefundDetailResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private RefundRecord refund;
        private Map<String, Object> transactionDetail;
    }

    /** 页面提交的审批决策。 */
    @Data
    public static class ApprovalDecisionRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String decisionRequestId;
        private Integer expectedVersion;
        private String approvalReason;
    }

    /** Admin 调用 Payment 的审批请求。 */
    @Data
    public static class ApprovalClientRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String decisionRequestId;
        private Integer expectedVersion;
        private String operatorId;
        private String operatorName;
        private String approvalReason;
    }

    /** 审批结果。 */
    @Data
    public static class ApprovalResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private String approvalId;
        private String refundTransactionId;
        private String approvalStatus;
        private String approvalOperatorId;
        private String approvalOperatorName;
        private LocalDateTime approvalTime;
        private String approvalReason;
        private String executionEventId;
        private Integer version;
    }
}
