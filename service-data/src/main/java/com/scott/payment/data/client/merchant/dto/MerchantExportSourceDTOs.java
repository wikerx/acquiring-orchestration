package com.scott.payment.data.client.merchant.dto;

import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantRefundQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantFundLedgerQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantSettlementBatchQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantSettlementReserveQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantSettlementSummaryQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantSettlementTransactionQuery;
import com.scott.payment.data.api.internal.dto.ExportTaskInternalDTOs.MerchantTransactionQuery;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Data 调用 Merchant 导出来源的客户端模型，与 Data 文件行模型保持隔离。
 */
public final class MerchantExportSourceDTOs {

    /**
     * 阻止 Merchant 导出来源客户端模型容器被实例化。
     */
    private MerchantExportSourceDTOs() {
    }

    /**
     * 商户交易导出分页请求。
     */
    @Data
    public static class TransactionPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private MerchantTransactionQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 商户退款导出分页请求。
     */
    @Data
    public static class RefundPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private MerchantRefundQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 商户结算批次导出分页请求。
     */
    @Data
    public static class SettlementBatchPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private MerchantSettlementBatchQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 商户批次结算汇总导出分页请求。
     */
    @Data
    public static class SettlementSummaryPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private MerchantSettlementSummaryQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 商户交易结算导出分页请求。
     */
    @Data
    public static class SettlementTransactionPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private MerchantSettlementTransactionQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 商户保证金结算导出分页请求。
     */
    @Data
    public static class SettlementReservePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private MerchantSettlementReserveQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 商户余额流水导出分页请求。
     */
    @Data
    public static class FundLedgerPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private MerchantFundLedgerQuery query;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 商户交易安全导出来源行。
     */
    @Data
    public static class TransactionRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String transactionId;
        private String sourceTransactionId;
        private String merchantOrderNo;
        private String merchantOrderId;
        private String transactionType;
        private String transactionStatus;
        private BigDecimal transactionAmount;
        private String transactionCurrency;
        private String merchantResponseCode;
        private String merchantResponseMessage;
        private String merchantNotificationStatus;
        private String paymentMethod;
        private String paymentBrand;
        private String cardBin;
        private String cardNumberMasked;
        private Integer threeDsEnabled;
        private String authCode;
        private String settlementStatus;
        private LocalDateTime transactionDateTime;
        private LocalDateTime operationTime;
    }

    /**
     * 商户退款安全导出来源行。
     */
    @Data
    public static class RefundRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String refundTransactionId;
        private String sourceTransactionId;
        private String merchantOrderNo;
        private String transactionType;
        private String refundScope;
        private BigDecimal transactionAmount;
        private String transactionCurrency;
        private String transactionStatus;
        private String approvalStatus;
        private String merchantVisibleMessage;
        private String paymentMethod;
        private LocalDateTime transactionDateTime;
        private LocalDateTime completeTime;
    }

    /**
     * 商户结算批次安全导出来源行。
     */
    @Data
    public static class SettlementBatchRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private LocalDate businessDate;
        private String batchType;
        private String batchStatus;
        private String targetCurrency;
        private Long transactionCount;
        private Integer settlementItemCount;
        private String netDirection;
        private BigDecimal netAmount;
        private LocalDateTime postedTime;
    }

    /**
     * 商户批次结算汇总安全导出来源行。
     */
    @Data
    public static class SettlementSummaryRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String paymentType;
        private String paymentMethod;
        private String transactionType;
        private String resultItemType;
        private String feeCategory;
        private String direction;
        private String sourceCurrency;
        private BigDecimal sourceAmount;
        private String targetCurrency;
        private BigDecimal targetAmount;
        private Long transactionCount;
    }

    /**
     * 商户交易结算安全导出来源行。
     */
    @Data
    public static class SettlementTransactionRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private LocalDate businessDate;
        private String merchantOrderNo;
        private String sourceTransactionId;
        private LocalDateTime sourceTransactionDateTime;
        private String paymentType;
        private String paymentMethod;
        private String transactionType;
        private BigDecimal sourceAmount;
        private String sourceCurrency;
        private Long componentCount;
        private String netDirection;
        private BigDecimal netTargetAmount;
        private String targetCurrency;
        private LocalDateTime postedTime;
    }

    /**
     * 商户保证金结算安全导出来源行。
     */
    @Data
    public static class SettlementReserveRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private LocalDate businessDate;
        private String reserveNo;
        private String sourceTransactionId;
        private LocalDateTime sourceTransactionDateTime;
        private String actionType;
        private String direction;
        private String currency;
        private BigDecimal amount;
        private BigDecimal remainingAmount;
        private String reserveStatus;
        private LocalDate expectedReleaseDate;
        private LocalDateTime actionTime;
    }

    /**
     * 商户余额流水安全导出来源行。
     */
    @Data
    public static class FundLedgerRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String ledgerNo;
        private String businessType;
        private String summary;
        private String businessNo;
        private String balanceType;
        private String direction;
        private BigDecimal amount;
        private String currency;
        private BigDecimal balanceBefore;
        private BigDecimal balanceAfter;
        private String operatorName;
        private String reviewerName;
        private LocalDateTime postedTime;
    }

    /**
     * 商户交易导出分页响应。
     */
    @Data
    public static class TransactionPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<TransactionRow> records = new ArrayList<>();
    }

    /**
     * 商户退款导出分页响应。
     */
    @Data
    public static class RefundPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<RefundRow> records = new ArrayList<>();
    }

    /**
     * 商户结算批次导出分页响应。
     */
    @Data
    public static class SettlementBatchPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<SettlementBatchRow> records = new ArrayList<>();
    }

    /**
     * 商户批次结算汇总导出分页响应。
     */
    @Data
    public static class SettlementSummaryPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<SettlementSummaryRow> records = new ArrayList<>();
    }

    /**
     * 商户交易结算导出分页响应。
     */
    @Data
    public static class SettlementTransactionPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<SettlementTransactionRow> records = new ArrayList<>();
    }

    /**
     * 商户保证金结算导出分页响应。
     */
    @Data
    public static class SettlementReservePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<SettlementReserveRow> records = new ArrayList<>();
    }

    /**
     * 商户余额流水导出分页响应。
     */
    @Data
    public static class FundLedgerPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<FundLedgerRow> records = new ArrayList<>();
    }
}
