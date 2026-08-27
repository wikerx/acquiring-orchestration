package com.scott.payment.clearing.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 补偿扫描的一次性数据库投影，不对应独立物理表。 */
@Data
public class ClearingCompensationCandidateDO {
    private Long operationRowId;
    private String transactionId;
    private String operationId;
    private String merchantId;
    private String merchantOrderNo;
    private String sourceTransactionId;
    private String transactionType;
    private String transactionStatus;
    private String labelCurrency;
    private BigDecimal labelAmount;
    private String approvedCurrency;
    private BigDecimal approvedAmount;
    private String transactionCurrency;
    private BigDecimal transactionAmount;
    private Integer currencyExponent;
    private LocalDateTime transactionDateTime;
    private LocalDateTime transactionUtcTime;
    private String transactionTimeZone;
    private Integer operationVersion;
    private String operationClearingStatus;
    private String financeStateId;
    private String clearingStatus;
    private Integer clearingRevision;
    private Integer clearingRetryCount;
    private LocalDateTime nextRetryTime;
    private String lastFailureCode;
    private LocalDateTime processingDeadline;
    private Integer financeStateVersion;
    private String reason;
}
