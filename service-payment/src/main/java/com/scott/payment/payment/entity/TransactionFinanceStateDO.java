package com.scott.payment.payment.entity;

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
 * @classname : TransactionFinanceStateDO
 * @date : 2026-08-14 13:45
 * @email : scott_x@163.com
 * @description : 交易财务状态持久化实体，仅承载已形成的结算、费用、对账和入账事实。
 * @status : create
 */
@Data
@TableName("transaction_finance_state")
public class TransactionFinanceStateDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String financeStateId;
    private String transactionId;
    private String operationId;
    private String transactionType;
    private String settlementStatus;
    private String settlementCurrency;
    private BigDecimal settlementRate;
    private BigDecimal settlementAmount;
    private BigDecimal settlementFeeAmount;
    private String feeItemsJson;
    private LocalDate settlementDate;
    private String settlementCycle;
    private String settlementBatchNo;
    private String reconciliationStatus;
    private LocalDate reconciliationDate;
    private String reconciliationBatchNo;
    private String accountingStatus;
    private LocalDateTime accountingTime;
    private String channelFeeCurrency;
    private BigDecimal channelFeeAmount;
    private String platformFeeCurrency;
    private BigDecimal platformFeeAmount;
    private String merchantReceivableCurrency;
    private BigDecimal merchantReceivableAmount;
    private String reserveCurrency;
    private BigDecimal reserveAmount;
    private String netSettlementCurrency;
    private BigDecimal netSettlementAmount;
    private LocalDateTime transactionDateTime;
    private LocalDateTime transactionUtcTime;
    private String transactionTimeZone;
    private Integer version;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
