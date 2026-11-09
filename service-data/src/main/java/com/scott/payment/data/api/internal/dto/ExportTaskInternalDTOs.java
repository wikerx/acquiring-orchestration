package com.scott.payment.data.api.internal.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 异步导出任务内部接口模型，承载经过来源服务鉴权的数据范围快照和任务状态。
 *
 * <p>浏览器不能直接访问本契约；查询条件和数据范围只能由已认证的管理服务创建。</p>
 */
public final class ExportTaskInternalDTOs {

    private ExportTaskInternalDTOs() {
    }

    /**
     * 统一运营待办导出条件快照，与页面查询字段保持同义但不携带分页参数。
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
     * 管理端交易主单导出条件快照，不包含页面分页参数和服务端商户范围。
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
     * 管理端商户通知导出条件快照，不包含页面分页参数和服务端商户范围。
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
     * 管理端退款导出条件快照，完整保留筛选语义但不携带分页参数。
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
     * 管理端结算交易明细导出条件快照，不包含页面分页参数和可信商户范围。
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
     * 管理端保证金结算明细导出条件快照，不包含页面分页参数和可信商户范围。
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
     * 创建导出任务命令；商户范围由管理服务根据当前账号重新计算后写入。
     */
    @Data
    public static class CreateRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @NotBlank
        @Size(max = 64)
        private String taskType;
        @NotBlank
        @Size(max = 128)
        private String taskName;
        @NotNull
        private Long creatorAccountId;
        @NotBlank
        @Size(max = 128)
        private String creatorAccountName;
        @Size(max = 16)
        private String locale;
        @Size(max = 64)
        private String timezone;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        private WorkItemQuery workItemQuery;
        @Valid
        private TransactionOrderQuery transactionOrderQuery;
        @Valid
        private TransactionOrderQuery transactionOperationQuery;
        @Valid
        private MerchantNotificationQuery merchantNotificationQuery;
        @Valid
        private RefundQuery refundQuery;
        @Valid
        private SettlementTransactionQuery settlementTransactionQuery;
        @Valid
        private SettlementReserveQuery settlementReserveQuery;
    }

    /**
     * 查询当前创建人的导出任务，第一批不提供跨账号查看能力。
     */
    @Data
    public static class SearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        @Size(max = 64)
        private String taskType;
        @Size(max = 32)
        private String status;
        private LocalDateTime createdStartTime;
        private LocalDateTime createdEndTime;
        @Min(1)
        private Integer pageNo;
        @Min(1)
        @Max(100)
        private Integer pageSize;
    }

    /**
     * 单任务身份请求，用于详情和重试时的归属校验。
     */
    @Data
    public static class TaskRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 64)
        private String taskNo;
        @NotNull
        private Long creatorAccountId;
        private Long expectedVersion;
    }

    /**
     * 下载命令，客户端 IP 仅写入安全审计，不参与对象键生成。
     */
    @Data
    @EqualsAndHashCode(callSuper = true)
    public static class DownloadRequest extends TaskRequest {
        private static final long serialVersionUID = 1L;
        @Size(max = 64)
        private String clientIp;
        @Size(max = 128)
        private String operatorName;
    }

    /**
     * Job 触发的有界领取和过期清理请求。
     */
    @Data
    public static class DispatchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Min(1)
        @Max(50)
        private Integer batchSize;
        @NotNull
        private LocalDateTime dispatchTime;
    }

    /**
     * 导出任务列表摘要，不返回对象键、条件 JSON 或数据范围 JSON。
     */
    @Data
    public static class TaskSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private String taskNo;
        private String taskType;
        private String taskName;
        private String status;
        private Integer progressPercent;
        private Long processedRows;
        private Long totalRows;
        private String fileName;
        private Long fileSizeBytes;
        private String fileSha256;
        private LocalDateTime expiresAt;
        private String failureCode;
        private String failureReason;
        private Integer retryCount;
        private Integer maxRetryCount;
        private Long version;
        private LocalDateTime createTime;
        private LocalDateTime startedAt;
        private LocalDateTime finishedAt;
    }

    /**
     * 单轮调度结果，只汇总数量，避免把任务条件和失败堆栈写入 Job 日志。
     */
    @Data
    public static class DispatchResult implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requestId;
        private Integer claimedCount;
        private Integer successCount;
        private Integer failedCount;
        private Integer expiredCount;
    }
}
