package com.scott.payment.merchant.dto.exporttask;

import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantRefundQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantFundLedgerQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantSettlementBatchQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantSettlementReserveQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantSettlementSummaryQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantSettlementTransactionQuery;
import com.scott.payment.merchant.client.data.dto.MerchantDataExportTaskClientDTOs.MerchantTransactionQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * service-data 分页读取商户导出安全投影的内部契约，不包含渠道或平台成本字段。
 */
public final class MerchantExportSourceDTOs {

    private MerchantExportSourceDTOs() {
    }

    /**
     * 商户交易导出单页请求，商户号来自任务冻结快照。
     */
    @Data
    public static class TransactionPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        private String merchantId;
        @Valid
        @NotNull
        private MerchantTransactionQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /**
     * 商户退款导出单页请求，商户号来自任务冻结快照。
     */
    @Data
    public static class RefundPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        private String merchantId;
        @Valid
        @NotNull
        private MerchantRefundQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /**
     * 商户结算批次导出单页请求，商户号来自任务冻结快照。
     */
    @Data
    public static class SettlementBatchPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        private String merchantId;
        @Valid
        @NotNull
        private MerchantSettlementBatchQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /**
     * 商户批次结算汇总导出单页请求，来源服务会复核批次归属。
     */
    @Data
    public static class SettlementSummaryPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        private String merchantId;
        @Valid
        @NotNull
        private MerchantSettlementSummaryQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /**
     * 商户交易结算明细导出单页请求，商户号来自任务冻结快照。
     */
    @Data
    public static class SettlementTransactionPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        private String merchantId;
        @Valid
        @NotNull
        private MerchantSettlementTransactionQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /**
     * 商户保证金结算明细导出单页请求，商户号来自任务冻结快照。
     */
    @Data
    public static class SettlementReservePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        private String merchantId;
        @Valid
        @NotNull
        private MerchantSettlementReserveQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /**
     * 商户余额流水导出单页请求，商户号和账户主键均来自任务冻结快照。
     */
    @Data
    public static class FundLedgerPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        private String merchantId;
        @Valid
        @NotNull
        private MerchantFundLedgerQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(500)
        private Integer pageSize;
    }

    /**
     * 商户交易安全导出行，只保留商户提交和平台返回口径。
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
     * 商户退款安全导出行，只保留标签金额、商户状态和可见说明。
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
     * 商户结算批次安全导出行，只保留批次净入账和商户可见状态。
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
     * 商户批次结算汇总安全导出行，金额来自不可变结算结果。
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
     * 商户交易结算安全导出行，不包含渠道成本、渠道币种或渠道汇率。
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
     * 商户保证金结算安全导出行，金额和币种保持原标签责任口径。
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
     * 商户余额流水安全导出行，不包含内部幂等键、请求号或平台账户信息。
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
     * 商户交易导出单页响应。
     */
    @Data
    public static class TransactionPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<TransactionRow> records = new ArrayList<>();
    }

    /**
     * 商户退款导出单页响应。
     */
    @Data
    public static class RefundPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<RefundRow> records = new ArrayList<>();
    }

    /**
     * 商户结算批次导出单页响应。
     */
    @Data
    public static class SettlementBatchPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<SettlementBatchRow> records = new ArrayList<>();
    }

    /**
     * 商户批次结算汇总导出单页响应。
     */
    @Data
    public static class SettlementSummaryPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<SettlementSummaryRow> records = new ArrayList<>();
    }

    /**
     * 商户交易结算导出单页响应。
     */
    @Data
    public static class SettlementTransactionPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<SettlementTransactionRow> records = new ArrayList<>();
    }

    /**
     * 商户保证金结算导出单页响应。
     */
    @Data
    public static class SettlementReservePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<SettlementReserveRow> records = new ArrayList<>();
    }

    /**
     * 商户余额流水导出单页响应。
     */
    @Data
    public static class FundLedgerPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private List<FundLedgerRow> records = new ArrayList<>();
    }
}
