package com.scott.payment.settlement.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementResultItemDO
 * @date : 2026-08-26 23:30
 * @email : scott_x@163.com
 * @description : 结算不可变结果明细；TRACE 仅审计费用组件，FINANCIAL_COMPONENT 才参与批次汇总。
 * @status : create
 */
@Data
@TableName("settlement_result_item")
public class SettlementResultItemDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String settlementResultItemNo;
    private String settlementBatchNo;
    private Long candidateId;
    private Integer resultLineNo;
    private String merchantId;
    private Long settlementAccountId;
    private String sourceDetailType;
    private String sourceDetailNo;
    private Long reversalOfResultItemId;
    private String sourceTransactionId;
    private LocalDateTime sourceTransactionDateTime;
    private String feeGroupNo;
    private String resultItemType;
    private String resultRole;
    private String paymentType;
    private String paymentMethod;
    private String transactionType;
    private String feeCategory;
    private String direction;
    private BigDecimal sourceAmount;
    private String sourceCurrency;
    private Integer sourceCurrencyExponent;
    private Long settlementBatchRateId;
    private BigDecimal unroundedTargetAmount;
    private BigDecimal targetAmount;
    private String targetCurrency;
    private Integer targetCurrencyExponent;
    private String appliedLimit;
    private BigDecimal minimumTargetAmount;
    private BigDecimal maximumTargetAmount;
    private String roundingMode;
    private String formulaSnapshot;
    private String ledgerIdempotencyKey;
    private LocalDateTime createTime;
}
