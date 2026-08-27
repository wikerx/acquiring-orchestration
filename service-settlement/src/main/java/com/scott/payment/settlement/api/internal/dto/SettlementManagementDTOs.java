package com.scott.payment.settlement.api.internal.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementManagementDTOs
 * @date : 2026-08-26 21:10
 * @email : scott_x@163.com
 * @description : 结算内部管理接口契约；只暴露批次、锁定汇率、聚合结果和非敏感运行状态，不返回卡或账单数据。
 * @status : create
 */
public final class SettlementManagementDTOs {

    private SettlementManagementDTOs() {
    }

    /** 批次主键游标查询条件，业务日期范围最多93天。 */
    @Data
    public static class BatchSearchRequest {
        private String settlementBatchNo;
        private String merchantId;
        private String batchType;
        private String batchStatus;
        private LocalDate beginBusinessDate;
        private LocalDate endBusinessDate;
        private Long cursorId;
        private Integer limit;
    }

    /** 管理列表展示的批次权威状态快照。 */
    @Data
    public static class BatchSummary {
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

    /** 主键游标批次查询结果。 */
    @Data
    public static class BatchSearchResponse {
        private List<BatchSummary> records = Collections.emptyList();
        private boolean hasMore;
        private Long nextCursorId;
    }

    /** 批次锁定的直接汇率；同币种也必须保留恒等汇率身份。 */
    @Data
    public static class RateLine {
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

    /** 按支付类型、方式、交易类型、费用类别和币种形成的结算聚合。 */
    @Data
    public static class ResultSummaryLine {
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

    /** 每批唯一最终净入账结果；金额为非负值，方向采用商户视角。 */
    @Data
    public static class NetPosting {
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

    /** 资金提交后的交易投影和 FIFO Outbox 运行计数。 */
    @Data
    public static class OperationalState {
        private Long projectionTaskCount;
        private Long projectionCompletedCount;
        private Long projectionFailedCount;
        private Long outboxEventCount;
        private Long outboxSentCount;
        private Long outboxFailedCount;
    }

    /** 批次详情由有界集合组成，不返回候选级全量明细。 */
    @Data
    public static class BatchDetailResponse {
        private BatchSummary batch;
        private List<RateLine> rates = Collections.emptyList();
        private List<ResultSummaryLine> resultSummaries = Collections.emptyList();
        private NetPosting netPosting;
        private OperationalState operationalState;
    }

    /** Admin 受控命令；操作人由 Admin 登录上下文生成后再进入内部接口。 */
    @Data
    public static class BatchCommandRequest {
        private String requestKey;
        private Long expectedVersion;
        private String reason;
        private String operator;
    }

    /** 取消或冲正命令结果。 */
    @Data
    public static class BatchCommandResponse {
        private String settlementBatchNo;
        private String resultBatchNo;
        private String resultStatus;
        private Integer releasedCandidateCount;
    }
}
