package com.scott.payment.clearing.entity;

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
 * @classname : ClearingTransactionOperationDO
 * @date : 2026-08-26 09:12
 * @email : scott_x@163.com
 * @description : 清分服务对 transaction_operation 的只读持久化投影；所有访问必须携带 transaction_date_time。
 * @status : create
 */
@Data
@TableName("transaction_operation")
public class ClearingTransactionOperationDO implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    private String transactionId;
    private String operationId;
    private String sourceTransactionId;
    private String merchantId;
    private String merchantOrderNo;
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
    private Integer version;
}
