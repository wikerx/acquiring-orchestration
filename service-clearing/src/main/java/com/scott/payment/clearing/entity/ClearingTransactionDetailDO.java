package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionDetailDO
 * @date : 2026-08-26 10:35
 * @email : scott_x@163.com
 * @description : 动作级不可变交易清分明细实体，只保存本金、费用和返费原子事实，禁止混入保证金或结算汇率。
 * @status : create
 */
@Data
@TableName("transaction_clearing_detail")
public class ClearingTransactionDetailDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String clearingDetailNo;
    private String financeStateId;
    private String transactionId;
    private String operationId;
    private String sourceTransactionId;
    private String sourceClearingDetailNo;
    private String sourceSettlementResultItemNo;
    private String merchantId;
    /** 交易清分时冻结的支付类型，用于结算分组统计。 */
    private String paymentType;
    /** 交易清分时冻结的支付方式，用于结算分组统计。 */
    private String paymentMethod;
    private String transactionType;
    private Integer clearingRevision;
    private Integer lineNo;
    private String itemType;
    private String feeCategory;
    private String riskServiceType;
    private String itemCode;
    private String itemName;
    private String direction;
    private String labelCurrency;
    private BigDecimal labelAmount;
    private Integer labelCurrencyExponent;
    private String feeGroupNo;
    private Integer componentNo;
    private String componentType;
    private String basisCurrency;
    private BigDecimal basisAmount;
    private Integer basisCurrencyExponent;
    private BigDecimal amount;
    private String currency;
    private Integer currencyExponent;
    private Long feePlanId;
    private Long feePlanVersionId;
    private Integer feePlanVersionNo;
    private Long feeRuleId;
    private Long feeRuleTierId;
    private String chargeTrigger;
    private String feeMode;
    private String tierPeriodKey;
    private String tierMetric;
    private Long tierCountBefore;
    private Long tierCountDelta;
    private Long tierCountAfter;
    private BigDecimal tierAmountUsdBefore;
    private BigDecimal tierAmountUsdDelta;
    private BigDecimal tierAmountUsdAfter;
    private BigDecimal percentageRate;
    private BigDecimal fixedAmountUsd;
    private BigDecimal minimumAmountUsd;
    private BigDecimal maximumAmountUsd;
    private String limitEvaluationStatus;
    private String appliedLimit;
    private String roundingMode;
    private String formulaSnapshot;
    private String ruleSnapshotJson;
    private String feeSnapshotHash;
    private LocalDate settlementEligibleDate;
    private String recordStatus;
    private LocalDateTime transactionDateTime;
    private LocalDateTime transactionUtcTime;
    private String transactionTimeZone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
