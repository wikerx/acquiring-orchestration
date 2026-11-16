package com.scott.payment.merchant.dto.exporttask;

import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundQuery;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery;
import com.scott.payment.merchant.dto.MerchantFinanceDTOs.DetailQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.BatchQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.ReserveItemQuery;
import com.scott.payment.merchant.dto.settlement.MerchantSettlementDTOs.TransactionItemQuery;
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
 * 商户异步导出与下载中心浏览器契约，账号和商户归属始终由认证上下文补齐。
 */
public final class MerchantExportTaskDTOs {

    private MerchantExportTaskDTOs() {
    }

    /**
     * 商户交易查询异步导出提交命令。
     */
    @Data
    public static class TransactionSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private TransactionPageQuery query;
    }

    /**
     * 商户退款异步导出提交命令。
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
     * 商户结算批次异步导出提交命令。
     */
    @Data
    public static class SettlementBatchSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private BatchQuery query;
    }

    /**
     * 商户指定批次结算汇总异步导出提交命令。
     */
    @Data
    public static class SettlementSummarySubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @NotBlank
        @Size(max = 64)
        private String settlementBatchNo;
    }

    /**
     * 商户交易结算明细异步导出提交命令。
     */
    @Data
    public static class SettlementTransactionSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private TransactionItemQuery query;
    }

    /**
     * 商户保证金结算明细异步导出提交命令。
     */
    @Data
    public static class SettlementReserveSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private ReserveItemQuery query;
    }

    /**
     * 商户资金账户余额流水异步导出提交命令。
     */
    @Data
    public static class FundLedgerSubmitRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotBlank
        @Size(max = 128)
        private String requestId;
        @Valid
        @NotNull
        private DetailQuery query;
    }

    /**
     * 当前商户账号下载中心查询条件。
     */
    @Data
    public static class SearchRequest implements Serializable {
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
     * 商户可见导出任务摘要，不包含对象键、条件快照和数据范围快照。
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
