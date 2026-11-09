package com.scott.payment.admin.dto.exporttask;

import com.scott.payment.admin.dto.operation.OperationalWorkItemDTOs.SearchRequest;
import com.scott.payment.admin.dto.transaction.AdminRefundDTOs.RefundQuery;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ReserveItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminSettlementDTOs.ResultItemSearchRequest;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理端异步导出与下载中心契约，浏览器只能提交查询条件和幂等键。
 */
public final class AdminExportTaskDTOs {

    private AdminExportTaskDTOs() {
    }

    /**
     * 统一待办导出提交命令，商户范围和操作人由服务端可信上下文补齐。
     */
    @Data
    public static class WorkItemSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private SearchRequest query;
    }

    /**
     * 交易主单异步导出提交命令，账号和商户范围由服务端可信上下文补齐。
     */
    @Data
    public static class TransactionOrderSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private TransactionPageQuery query;
    }

    /**
     * 交易操作流水异步导出提交命令，账号和商户范围由服务端可信上下文补齐。
     */
    @Data
    public static class TransactionOperationSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private TransactionPageQuery query;
    }

    /**
     * 商户通知记录异步导出提交命令，账号和商户范围由服务端可信上下文补齐。
     */
    @Data
    public static class MerchantNotificationSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private MerchantNotificationQuery query;
    }

    /**
     * 退款异步导出提交命令，账号和商户范围由服务端可信上下文补齐。
     */
    @Data
    public static class RefundSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private RefundQuery query;
    }

    /**
     * 结算交易明细异步导出提交命令，账号和商户范围由服务端可信上下文补齐。
     */
    @Data
    public static class SettlementTransactionSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private ResultItemSearchRequest query;
    }

    /**
     * 保证金结算明细异步导出提交命令，账号和商户范围由服务端可信上下文补齐。
     */
    @Data
    public static class SettlementReserveSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private ReserveItemSearchRequest query;
    }

    /**
     * 当前操作人下载中心查询条件。
     */
    @Data
    public static class SearchRequestDTO implements Serializable {
        private static final long serialVersionUID = 1L;
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
     * 人工重试命令，预期版本用于阻止重复点击覆盖并发状态。
     */
    @Data
    public static class RetryRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long expectedVersion;
    }

    /**
     * 导出任务摘要，不包含对象键、查询快照和商户数据范围快照。
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
}
