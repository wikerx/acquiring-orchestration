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
 * @classname : ClearingReserveStateDO
 * @date : 2026-08-26 10:35
 * @email : scott_x@163.com
 * @description : 原支付保证金并发控制投影；退款必须按原支付 transaction_date_time 加行锁后再计算返还上限。
 * @status : create
 */
@Data
@TableName("transaction_reserve_clearing_state")
public class ClearingReserveStateDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reserveStateId;
    private String originalTransactionId;
    private String operationId;
    private String originalFinanceStateId;
    private String originalHoldDetailNo;
    private Long originalFeePlanVersionId;
    private String originalReserveSnapshotHash;
    private String merchantId;
    private String reserveCurrency;
    private Integer reserveCurrencyExponent;
    private BigDecimal originalBasisAmount;
    private BigDecimal originalReserveRate;
    private String originalRoundingMode;
    private BigDecimal retainedAmount;
    private BigDecimal returnedAmount;
    private BigDecimal releasedAmount;
    /** 经复核增加的累计保证金负债。 */
    private BigDecimal debitAdjustmentAmount;
    /** 经复核减少的累计保证金负债。 */
    private BigDecimal creditAdjustmentAmount;
    private BigDecimal remainingAmount;
    private LocalDate expectedReserveReleaseDate;
    private String reserveStatus;
    private String lastReturnTransactionId;
    private LocalDateTime lastReturnTransactionDateTime;
    private LocalDateTime transactionDateTime;
    private LocalDateTime originalTransactionUtcTime;
    private String transactionTimeZone;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
