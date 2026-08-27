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
 * @classname : ClearingReserveDetailDO
 * @date : 2026-08-26 10:35
 * @email : scott_x@163.com
 * @description : 独立保证金清分明细实体，始终使用原支付标签币种并引用原HOLD事实，不包含费用或汇率字段。
 * @status : create
 */
@Data
@TableName("transaction_reserve_clearing_detail")
public class ClearingReserveDetailDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String reserveClearingDetailNo;
    private String financeStateId;
    private String transactionId;
    private String operationId;
    private String originalTransactionId;
    private LocalDateTime originalTransactionDateTime;
    private String sourceReserveDetailNo;
    private String merchantId;
    /** 保证金事实形成时冻结的支付类型。 */
    private String paymentType;
    /** 保证金事实形成时冻结的支付方式。 */
    private String paymentMethod;
    private String transactionType;
    private Integer clearingRevision;
    private Integer lineNo;
    private String reserveActionType;
    private String itemCode;
    private String itemName;
    private String direction;
    private String reserveCurrency;
    private Integer reserveCurrencyExponent;
    private BigDecimal basisAmount;
    private BigDecimal reserveRate;
    private BigDecimal retainedAmount;
    private BigDecimal returnedAmount;
    private BigDecimal releasedAmount;
    private BigDecimal adjustmentAmount;
    private BigDecimal remainingAmount;
    private Long feePlanId;
    private Long feePlanVersionId;
    private Integer feePlanVersionNo;
    private String reserveSnapshotHash;
    private String reserveBasis;
    private String reserveDelayUnit;
    private Integer reserveDelayDays;
    private String roundingMode;
    private String formulaSnapshot;
    private LocalDate expectedReserveReleaseDate;
    private String recordStatus;
    private LocalDateTime transactionDateTime;
    private LocalDateTime transactionUtcTime;
    private String transactionTimeZone;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
