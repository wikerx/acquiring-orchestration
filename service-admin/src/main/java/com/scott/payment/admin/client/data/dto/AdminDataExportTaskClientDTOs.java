package com.scott.payment.admin.client.data.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Admin 调用 Data 异步导出内部接口的客户端模型，与浏览器契约保持隔离。
 */
public final class AdminDataExportTaskClientDTOs {

    private AdminDataExportTaskClientDTOs() {
    }

    /**
     * 创建统一待办导出任务时冻结的可信条件和商户范围。
     */
    @Data
    public static class CreateRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requestId;
        private String taskType;
        private String taskName;
        private Long creatorAccountId;
        private String creatorAccountName;
        private String locale;
        private String timezone;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        private WorkItemQuery workItemQuery;
        private TransactionOrderQuery transactionOrderQuery;
        private TransactionOrderQuery transactionOperationQuery;
        private MerchantNotificationQuery merchantNotificationQuery;
        private RefundQuery refundQuery;
        private SettlementTransactionQuery settlementTransactionQuery;
        private SettlementReserveQuery settlementReserveQuery;
    }

    /**
     * 冻结的统一待办筛选条件，不向 Data 传递页面当前页码。
     */
    @Data
    public static class WorkItemQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String workItemNo;
        private String keyword;
        private String sourceType;
        private String workStatus;
        private String priority;
        private Long ownerAccountId;
        private Boolean mineOnly;
        private Boolean unassignedOnly;
        private Boolean overdueOnly;
        private LocalDateTime detectedStartTime;
        private LocalDateTime detectedEndTime;
    }

    /**
     * 冻结的交易主单筛选条件，不向 Data 传递页面当前页码。
     */
    @Data
    public static class TransactionOrderQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String merchantOrderNo;
        private String transactionId;
        private String sourceTransactionId;
        private String transactionType;
        private String transactionStatus;
        private String channelCode;
        private String paymentMethod;
        private String paymentBrand;
        private String cardBin;
        private String channelOrderNo;
        private String merchantResponseCode;
        private String channelResponseCode;
        private String authCode;
        private String acquirerReferenceNo;
        private String channelMatchStatus;
        private String reconciliationStatus;
        private String settlementStatus;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private String queryTimeZone;
    }

    /**
     * 冻结的商户通知筛选条件，不向 Data 传递页面当前页码。
     */
    @Data
    public static class MerchantNotificationQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String merchantId;
        private String transactionId;
        private String notifyStatus;
        private LocalDateTime beginTime;
        private LocalDateTime endTime;
        private String queryTimeZone;
    }

    /**
     * 冻结的退款筛选条件，不向 Data 传递页面当前页码。
     */
    @Data
    public static class RefundQuery implements Serializable {
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

    /**
     * 冻结的结算交易明细筛选条件，不携带页面分页参数或可信商户范围。
     */
    @Data
    public static class SettlementTransactionQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private String merchantId;
        private String sourceTransactionId;
        private String merchantOrderNo;
        private LocalDateTime beginTransactionTime;
        private LocalDateTime endTransactionTime;
        private String resultItemType;
        private String resultRole;
        private String direction;
        private String targetCurrency;
        private String sourceDetailType;
        private LocalDate beginBusinessDate;
        private LocalDate endBusinessDate;
    }

    /**
     * 冻结的保证金结算明细筛选条件，不携带页面分页参数或可信商户范围。
     */
    @Data
    public static class SettlementReserveQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private String merchantId;
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
     * 查询当前创建人任务的内部请求。
     */
    @Data
    public static class SearchRequestDTO implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long creatorAccountId;
        private String taskType;
        private String status;
        private LocalDateTime createdStartTime;
        private LocalDateTime createdEndTime;
        private Integer pageNo;
        private Integer pageSize;
    }

    /**
     * 详情和重试共用的任务归属请求。
     */
    @Data
    public static class TaskRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String taskNo;
        private Long creatorAccountId;
        private Long expectedVersion;
    }

    /**
     * 下载请求额外携带操作人和网络来源，仅用于 Data 安全审计。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DownloadRequest extends TaskRequest {
        private static final long serialVersionUID = 1L;
        private String clientIp;
        private String operatorName;
    }

}
