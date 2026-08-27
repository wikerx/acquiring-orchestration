package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementReserveClearingDetailDO
 * @date : 2026-08-26 23:10
 * @email : scott_x@163.com
 * @description : 结算侧只读保证金清分事实投影；保证金仍以标签币种保存，仅在结算结果阶段使用批次统一汇率。
 * @status : create
 */
@Data
public class SettlementReserveClearingDetailDO {
    private Long id;
    private String reserveClearingDetailNo;
    private String financeStateId;
    private String transactionId;
    private String operationId;
    private String originalTransactionId;
    private LocalDateTime originalTransactionDateTime;
    private String sourceReserveDetailNo;
    private String merchantId;
    private String paymentType;
    private String paymentMethod;
    private String transactionType;
    private Integer clearingRevision;
    private Integer lineNo;
    private String reserveActionType;
    private String direction;
    private String reserveCurrency;
    private Integer reserveCurrencyExponent;
    private BigDecimal retainedAmount;
    private BigDecimal returnedAmount;
    private BigDecimal releasedAmount;
    private BigDecimal adjustmentAmount;
    private String roundingMode;
    private String formulaSnapshot;
    private LocalDate expectedReserveReleaseDate;
    private String recordStatus;
    private LocalDateTime transactionDateTime;
}
