package com.scott.payment.payment.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionAmountChangeLogDO
 * @date : 2026-07-14 19:36
 * @email : scott_x@163.com
 * @description : 交易金额变动日志实体，位于 service-payment 持久化层，记录授权、请款、退款、撤销等动作对生命周期累计金额的影响。
 * @status : create
 */
@Data
@TableName("transaction_amount_change_log")
public class TransactionAmountChangeLogDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private String amountChangeId;

    private String transactionId;

    private String operationId;

    private String sourceTransactionId;

    private String changeType;

    private String amountCurrency;

    private BigDecimal changeAmount;

    private BigDecimal authorizedBefore;

    private BigDecimal authorizedAfter;

    private BigDecimal capturedBefore;

    private BigDecimal capturedAfter;

    private BigDecimal refundedBefore;

    private BigDecimal refundedAfter;

    private BigDecimal availableCaptureBefore;

    private BigDecimal availableCaptureAfter;

    private BigDecimal availableRefundBefore;

    private BigDecimal availableRefundAfter;

    private String changeReason;

    private LocalDateTime changeTime;

    private LocalDateTime transactionDateTime;

    private LocalDateTime transactionUtcTime;

    private String transactionTimeZone;

    private LocalDateTime createTime;
}
