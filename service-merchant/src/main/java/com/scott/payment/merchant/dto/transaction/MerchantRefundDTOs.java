package com.scott.payment.merchant.dto.transaction;

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
 * @classname : MerchantRefundDTOs
 * @date : 2026-08-06 16:10
 * @email : scott_x@163.com
 * @description : 商户端退款查询模型，只保留当前商户可见的退款事实和统一状态说明，排除渠道及内部审批信息。
 * @status : create
 */
public final class MerchantRefundDTOs {

    private MerchantRefundDTOs() {
    }

    /** 商户退款查询条件；merchantId 仅由服务端认证上下文写入。 */
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
        private String labelCurrency;
        private String transactionCurrency;
        private BigDecimal minimumTransactionAmount;
        private BigDecimal maximumTransactionAmount;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private LocalDateTime completeBeginTime;
        private LocalDateTime completeEndTime;
        private String queryTimeZone;
    }

    /** 商户可见退款记录。 */
    @Data
    public static class RefundRecord implements Serializable {
        private static final long serialVersionUID = 1L;
        private String refundTransactionId;
        private String sourceTransactionId;
        private String merchantOrderNo;
        private String merchantOperationNo;
        private String transactionType;
        private String refundScope;
        private String requestSource;
        private String requestReason;
        private String transactionStatus;
        private String processStage;
        private String labelCurrency;
        private BigDecimal labelAmount;
        private String transactionCurrency;
        private BigDecimal transactionAmount;
        private Integer currencyExponent;
        private String paymentMethod;
        private String paymentBrand;
        private String merchantNotificationStatus;
        private LocalDateTime transactionDateTime;
        private LocalDateTime completeTime;
        private String approvalId;
        private String approvalStatus;
        private LocalDateTime approvalTime;
        private String approvalReason;
        private LocalDateTime approvalExpireTime;
        private Integer approvalVersion;
        private String merchantVisibleMessage;
    }

    /** 分币种退款金额统计。 */
    @Data
    public static class RefundCurrencySummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private String currency;
        private BigDecimal totalAmount = BigDecimal.ZERO;
        private BigDecimal pendingApprovalAmount = BigDecimal.ZERO;
        private BigDecimal successfulAmount = BigDecimal.ZERO;
        private BigDecimal pendingAmount = BigDecimal.ZERO;
    }

    /** 商户退款状态和金额统计。 */
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

    /** 商户退款分页和统计。 */
    @Data
    public static class RefundSearchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private PageResult<RefundRecord> page;
        private RefundSummary summary;
    }

    /** 商户退款详情，仅返回商户可见记录。 */
    @Data
    public static class RefundDetailResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private RefundRecord refund;
    }
}
