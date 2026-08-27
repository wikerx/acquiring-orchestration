package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionFinanceStateDO
 * @date : 2026-08-26 09:12
 * @email : scott_x@163.com
 * @description : 动作级清分权威状态持久化实体；状态更新只能通过带分片时间、当前状态和版本的 Mapper CAS。
 * @status : create
 */
@Data
@TableName("transaction_finance_state")
public class ClearingTransactionFinanceStateDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String financeStateId;
    private String transactionId;
    private String operationId;
    private String merchantId;
    private String sourceTransactionId;
    private String labelCurrency;
    private String transactionType;
    private String clearingStatus;
    private Integer clearingRevision;
    private String processingOwner;
    private LocalDateTime processingDeadline;
    private Integer clearingRetryCount;
    private LocalDateTime nextRetryTime;
    private String lastFailureCode;
    private String lastFailureMessage;
    private Long feePlanId;
    private Long feePlanVersionId;
    private Integer feePlanVersionNo;
    private String feeSnapshotHash;
    private BigDecimal grossLabelAmount;
    private Integer feeComponentCurrencyCount;
    private String feeEvaluationStatus;
    private String settlementStatus;
    private String settlementCurrency;
    private LocalDate settlementEligibleDate;
    private String platformFeeCurrency;
    private BigDecimal platformFeeAmount;
    private BigDecimal feeReversalAmount;
    private String merchantReceivableCurrency;
    private BigDecimal merchantReceivableAmount;
    private String reserveCurrency;
    private BigDecimal reserveAmount;
    private BigDecimal reserveReversalAmount;
    private LocalDate expectedReserveReleaseDate;
    private String netSettlementCurrency;
    private BigDecimal netSettlementAmount;
    private LocalDateTime transactionDateTime;
    private LocalDateTime transactionUtcTime;
    private String transactionTimeZone;
    private Integer version;
}
