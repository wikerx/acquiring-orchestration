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
 * @classname : ClearingReserveAdjustmentDO
 * @date : 2026-08-26 19:00
 * @email : scott_x@163.com
 * @description : 保证金差额调整的固定表审批事实；冻结标签币种、状态版本、提交人和复核执行结果。
 * @status : create
 */
@Data
@TableName("clearing_reserve_adjustment")
public class ClearingReserveAdjustmentDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String adjustmentNo;
    private String requestKey;
    private String reserveStateId;
    private String originalTransactionId;
    private LocalDateTime originalTransactionDateTime;
    private String merchantId;
    private String reserveCurrency;
    private Integer reserveCurrencyExponent;
    private String direction;
    private BigDecimal adjustmentAmount;
    private LocalDate requestedReleaseDate;
    private Long expectedReserveStateVersion;
    private String reason;
    private String submitOperator;
    private String reviewOperator;
    private String reviewComment;
    private String adjustmentStatus;
    private String executionTransactionId;
    private Integer sourceRevision;
    private LocalDateTime reviewTime;
    private LocalDateTime executedTime;
    private Long version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
