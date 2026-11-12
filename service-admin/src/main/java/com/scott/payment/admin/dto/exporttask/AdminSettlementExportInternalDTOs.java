package com.scott.payment.admin.dto.exporttask;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Data 分页读取管理端结算导出源的内部契约，不暴露给浏览器调用。
 */
public final class AdminSettlementExportInternalDTOs {

    /**
     * 阻止内部契约容器被实例化。
     */
    private AdminSettlementExportInternalDTOs() {
    }

    /**
     * 结算交易明细冻结查询，不包含页面分页参数。
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
     * 保证金结算明细冻结查询，不包含页面分页参数。
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
     * Data 分页读取结算交易明细的请求，商户范围来自任务创建时的可信快照。
     */
    @Data
    public static class SettlementTransactionPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private SettlementTransactionQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(100)
        private Integer pageSize;
    }

    /**
     * Data 分页读取保证金结算明细的请求，商户范围来自任务创建时的可信快照。
     */
    @Data
    public static class SettlementReservePageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private SettlementReserveQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(100)
        private Integer pageSize;
    }

    /**
     * 一页结算交易明细安全投影及相同条件下的总行数。
     */
    @Data
    public static class SettlementTransactionPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<SettlementTransactionRow> records = new ArrayList<>();
    }

    /**
     * 一页保证金结算明细安全投影及相同条件下的总行数。
     */
    @Data
    public static class SettlementReservePageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<SettlementReserveRow> records = new ArrayList<>();
    }

    /**
     * 结算交易明细导出安全行，不包含候选主键、渠道信息或内部原始组件。
     */
    @Data
    public static class SettlementTransactionRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private LocalDate businessDate;
        private String batchStatus;
        private String merchantId;
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
     * 保证金结算明细导出安全行，保持原标签币种且不包含账户或内部来源信息。
     */
    @Data
    public static class SettlementReserveRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private LocalDate businessDate;
        private String merchantId;
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

    /** 结算审核单冻结查询，不包含页面分页参数。 */
    @Data
    public static class SettlementReviewQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String reviewOrderNo;
        private String merchantId;
        private String reviewType;
        private String reviewStatus;
        private LocalDate beginBusinessDate;
        private LocalDate endBusinessDate;
    }

    /** 结算入账流水冻结查询，不包含页面分页参数。 */
    @Data
    public static class SettlementPostingQuery implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private String merchantId;
        private String ledgerNo;
        private String direction;
        private String operationMode;
        private String currency;
        private LocalDateTime beginPostedTime;
        private LocalDateTime endPostedTime;
    }

    /** Data 分页读取结算审核单的请求。 */
    @Data
    public static class SettlementReviewPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private SettlementReviewQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(100)
        private Integer pageSize;
    }

    /** Data 分页读取结算入账流水的请求。 */
    @Data
    public static class SettlementPostingPageRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        @NotNull
        private Long creatorAccountId;
        private boolean allMerchants;
        private Set<String> merchantIds = new LinkedHashSet<>();
        @Valid
        @NotNull
        private SettlementPostingQuery query;
        @NotNull
        @Min(1)
        private Integer pageNo;
        @NotNull
        @Min(1)
        @Max(100)
        private Integer pageSize;
    }

    /** 一页结算审核单安全投影。 */
    @Data
    public static class SettlementReviewPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<SettlementReviewRow> records = new ArrayList<>();
    }

    /** 一页结算入账流水安全投影。 */
    @Data
    public static class SettlementPostingPageResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private long total;
        private long pageNo;
        private long pageSize;
        private List<SettlementPostingRow> records = new ArrayList<>();
    }

    /** 结算审核单安全导出行。 */
    @Data
    public static class SettlementReviewRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String reviewOrderNo;
        private String reviewType;
        private String createMode;
        private String merchantId;
        private LocalDate businessDate;
        private String targetCurrency;
        private Integer candidateCount;
        private String netDirection;
        private BigDecimal netAmount;
        private String reviewStatus;
        private String submittedByAccountName;
        private LocalDateTime submittedTime;
        private String decidedByAccountName;
        private String decisionAction;
        private LocalDateTime decisionTime;
        private String settlementBatchNo;
    }

    /** 结算入账流水安全导出行。 */
    @Data
    public static class SettlementPostingRow implements Serializable {
        private static final long serialVersionUID = 1L;
        private String ledgerNo;
        private String settlementBatchNo;
        private String merchantId;
        private Long accountId;
        private String businessType;
        private String direction;
        private BigDecimal amount;
        private String currency;
        private BigDecimal balanceBefore;
        private BigDecimal balanceAfter;
        private Long accountSequence;
        private String operationMode;
        private String operatorName;
        private String reviewerName;
        private String operationReason;
        private String reviewComment;
        private LocalDateTime postedTime;
        private String idempotencyKey;
        private Long reversalOfLedgerId;
    }
}
