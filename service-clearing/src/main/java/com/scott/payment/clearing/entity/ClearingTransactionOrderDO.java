package com.scott.payment.clearing.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingTransactionOrderDO
 * @date : 2026-08-26 10:30
 * @email : scott_x@163.com
 * @description : 清分服务对生命周期主单的最小投影，用于退款累计事实和清分聚合状态 CAS，不修改交易状态或金额。
 * @status : create
 */
@Data
@TableName("transaction_order")
public class ClearingTransactionOrderDO {
    private Long id;
    private String operationId;
    private String merchantId;
    private String transactionCurrency;
    private BigDecimal transactionAmount;
    private BigDecimal refundedAmount;
    private String clearingStatus;
    private LocalDateTime transactionDateTime;
    private Integer version;
}
