package com.scott.payment.admin.dto.transaction;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminSettlementDTOs
 * @date : 2026-08-26 21:20
 * @email : scott_x@163.com
 * @description : Admin 结算批次管理契约；浏览器命令不允许携带操作人，应用层从登录上下文补齐。
 * @status : create
 */
public final class AdminSettlementDTOs {

    private AdminSettlementDTOs() {
    }

    @Data
    public static class BatchSearchRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private String merchantId;
        private String batchType;
        private String batchStatus;
        private LocalDate beginBusinessDate;
        private LocalDate endBusinessDate;
        private Integer pageNo;
        private Integer pageSize;
        /** 仅保留给旧版 service-settlement 内部查询客户端兼容，Admin 页面不再使用。 */
        private Long cursorId;
        /** 仅保留给旧版 service-settlement 内部查询客户端兼容，Admin 页面不再使用。 */
        private Integer limit;
    }

    @Data
    public static class BatchSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private String settlementBatchNo;
        private String displayBatchNo;
        private LocalDate businessDate;
        private String businessTimeZone;
        private Integer dailySequence;
        private String merchantId;
        private Long settlementProfileId;
        private Long settlementAccountId;
        private String targetCurrency;
        private Integer targetCurrencyExponent;
        private String batchType;
        private String originalBatchNo;
        private String batchStatus;
        private Integer candidateCount;
        private Integer retryCount;
        private String lastFailureStage;
        private String lastFailureCode;
        private String lastFailureMessage;
        private LocalDateTime rateLockedTime;
        private LocalDateTime calculatedTime;
        private LocalDateTime postedTime;
        private LocalDateTime cancelledTime;
        private Long version;
        private LocalDateTime createTime;
        private LocalDateTime updateTime;
    }

    @Data
    public static class BatchSearchResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private List<BatchSummary> records = Collections.emptyList();
        private boolean hasMore;
        private Long nextCursorId;
    }

    @Data
    public static class RateLine implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private String sourceCurrency;
        private String targetCurrency;
        private String rateType;
        private BigDecimal directRate;
        private Integer sourceCurrencyExponent;
        private Integer targetCurrencyExponent;
        private String rateSource;
        private String quoteId;
        private String sourceQuoteDirection;
        private LocalDateTime effectiveTime;
        private LocalDateTime lockedTime;
        private String lockedBy;
    }

    @Data
    public static class ResultSummaryLine implements Serializable {
        private static final long serialVersionUID = 1L;
        private String paymentType;
        private String paymentMethod;
        private String transactionType;
        private String resultItemType;
        private String feeCategory;
        private String direction;
        private String sourceCurrency;
        private String targetCurrency;
        private Long transactionCount;
        private BigDecimal sourceAmount;
        private BigDecimal targetAmount;
    }

    @Data
    public static class NetPosting implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long id;
        private String settlementResultItemNo;
        private Long reversalOfResultItemId;
        private String direction;
        private BigDecimal targetAmount;
        private String targetCurrency;
        private Integer targetCurrencyExponent;
        private String ledgerIdempotencyKey;
        private String formulaSnapshot;
        private LocalDateTime createTime;
    }

    @Data
    public static class OperationalState implements Serializable {
        private static final long serialVersionUID = 1L;
        private Long projectionTaskCount;
        private Long projectionCompletedCount;
        private Long projectionFailedCount;
        private Long outboxEventCount;
        private Long outboxSentCount;
        private Long outboxFailedCount;
    }

    @Data
    public static class BatchDetailResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private BatchSummary batch;
        private List<RateLine> rates = Collections.emptyList();
        private List<ResultSummaryLine> resultSummaries = Collections.emptyList();
        private NetPosting netPosting;
        private OperationalState operationalState;
    }

    /** 浏览器命令不包含 operator，避免伪造操作人。 */
    @Data
    public static class BatchCommandRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requestKey;
        private Long expectedVersion;
        private String reason;
    }

    /** service-admin 补齐可信操作人后的内部命令。 */
    @Data
    public static class InternalBatchCommandRequest implements Serializable {
        private static final long serialVersionUID = 1L;
        private String requestKey;
        private Long expectedVersion;
        private String reason;
        private String operator;
    }

    @Data
    public static class BatchCommandResponse implements Serializable {
        private static final long serialVersionUID = 1L;
        private String settlementBatchNo;
        private String resultBatchNo;
        private String resultStatus;
        private Integer releasedCandidateCount;
    }
}
