package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementTransactionClearingDetailDO
 * @date : 2026-08-26 23:10
 * @email : scott_x@163.com
 * @description : 结算侧只读交易清分事实投影；保留原币种组件、USD 限额及支付分组快照，不重新计算清分费用。
 * @status : create
 */
@Data
public class SettlementTransactionClearingDetailDO {
    private Long id;
    private String clearingDetailNo;
    private String financeStateId;
    private String transactionId;
    private String operationId;
    private String sourceTransactionId;
    private String merchantId;
    private String paymentType;
    private String paymentMethod;
    private String transactionType;
    private Integer clearingRevision;
    private Integer lineNo;
    private String itemType;
    private String feeCategory;
    private String direction;
    private String labelCurrency;
    private BigDecimal labelAmount;
    private Integer labelCurrencyExponent;
    private String feeGroupNo;
    private Integer componentNo;
    private String componentType;
    private BigDecimal amount;
    private String currency;
    private Integer currencyExponent;
    private BigDecimal minimumAmountUsd;
    private BigDecimal maximumAmountUsd;
    private String limitEvaluationStatus;
    private String appliedLimit;
    private String roundingMode;
    private String formulaSnapshot;
    private String recordStatus;
    private LocalDateTime transactionDateTime;
}
