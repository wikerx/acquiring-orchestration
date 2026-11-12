package com.scott.payment.merchant.client.data.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Merchant 调用 Data 异步导出内部接口的客户端模型，与浏览器契约和交易持久化模型隔离。
 */
public final class MerchantDataExportTaskClientDTOs {

    private MerchantDataExportTaskClientDTOs() {
    }

    /**
     * 创建商户导出任务时冻结的可信身份、商户号和唯一查询模型。
     */
    @Data
    public static class CreateRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requestId;
        private String taskType;
        private String taskName;
        private Long creatorAccountId;
        private String merchantId;
        private String creatorAccountName;
        private String locale;
        private String timezone;
        private MerchantTransactionQuery merchantTransactionQuery;
        private MerchantRefundQuery merchantRefundQuery;
        private MerchantSettlementBatchQuery merchantSettlementBatchQuery;
        private MerchantSettlementSummaryQuery merchantSettlementSummaryQuery;
        private MerchantSettlementTransactionQuery merchantSettlementTransactionQuery;
        private MerchantSettlementReserveQuery merchantSettlementReserveQuery;
        private MerchantFundLedgerQuery merchantFundLedgerQuery;
    }

    /**
     * 商户交易查询冻结条件，不包含分页和渠道内部筛选字段。
     */
    @Data
    public static class MerchantTransactionQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantOrderNo;
        private String transactionId;
        private String sourceTransactionId;
        private String transactionType;
        private String transactionStatus;
        private String paymentMethod;
        private String paymentBrand;
        private String merchantResponseCode;
        private String settlementStatus;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private String queryTimeZone;
    }

    /**
     * 商户退款冻结条件，只包含商户可见标签金额及状态筛选字段。
     */
    @Data
    public static class MerchantRefundQuery implements Serializable {
        private static final long serialVersionUID = 1L;
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
        private BigDecimal minimumTransactionAmount;
        private BigDecimal maximumTransactionAmount;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private LocalDateTime completeBeginTime;
        private LocalDateTime completeEndTime;
        private String queryTimeZone;
    }

    /**
     * 商户结算批次冻结条件，不包含分页和商户身份字段。
     */
    @Data
    public static class MerchantSettlementBatchQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private String batchType;
        private String batchStatus;
        private LocalDate beginBusinessDate;
        private LocalDate endBusinessDate;
    }

    /**
     * 商户批次结算汇总冻结条件，批次归属由来源服务按冻结商户号复核。
     */
    @Data
    public static class MerchantSettlementSummaryQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
    }

    /**
     * 商户交易结算明细冻结条件，只包含商户页面允许筛选的字段。
     */
    @Data
    public static class MerchantSettlementTransactionQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private String sourceTransactionId;
        private String merchantOrderNo;
        private LocalDateTime beginTransactionTime;
        private LocalDateTime endTransactionTime;
        private String paymentType;
        private String paymentMethod;
        private String transactionType;
        private String feeCategory;
        private LocalDate beginBusinessDate;
        private LocalDate endBusinessDate;
    }

    /**
     * 商户保证金结算明细冻结条件，币种始终表示原标签币种。
     */
    @Data
    public static class MerchantSettlementReserveQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private String reserveNo;
        private String reserveActionNo;
        private String sourceTransactionId;
        private String merchantOrderNo;
        private String reserveStatus;
        private String actionType;
        private String currency;
        private LocalDateTime beginTransactionTime;
        private LocalDateTime endTransactionTime;
        private LocalDate beginExpectedReleaseDate;
        private LocalDate endExpectedReleaseDate;
        private LocalDate beginBusinessDate;
        private LocalDate endBusinessDate;
    }

    /**
     * 商户余额流水冻结条件，账户主键由认证商户当前资金账户解析后写入。
     */
    @Data
    public static class MerchantFundLedgerQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long accountId;
        private String keyword;
        private String balanceType;
        private String businessType;
        private LocalDateTime postedStartTime;
        private LocalDateTime postedEndTime;
    }

    /**
     * 查询当前商户账号任务的内部请求。
     */
    @Data
    public static class SearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private String merchantId;
        private String taskType;
        private String status;
        private LocalDateTime createdStartTime;
        private LocalDateTime createdEndTime;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 详情和重试共用的商户任务归属请求。
     */
    @Data
    public static class TaskRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String taskNo;
        private Long creatorAccountId;
        private String merchantId;
        private Long expectedVersion;
    }

    /**
     * 下载请求额外携带操作人和网络来源，只用于 Data 下载审计。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DownloadRequest extends TaskRequest {
        private static final long serialVersionUID = 1L;
        private String clientIp;
        private String operatorName;
    }
}
