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
 * @classname : SettlementResultSummaryDO
 * @date : 2026-08-26 23:30
 * @email : scott_x@163.com
 * @description : 仅由 FINANCIAL_COMPONENT 生成的批次维度汇总，不包含 TRACE 或任何余额入账行。
 * @status : create
 */
@Data
@TableName("settlement_result_summary")
public class SettlementResultSummaryDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String settlementBatchNo;
    private String merchantId;
    private String paymentType;
    private String paymentMethod;
    private String transactionType;
    private String resultItemType;
    private String feeCategory;
    private String direction;
    private String sourceCurrency;
    private String targetCurrency;
    private Long transactionCount;
    private BigDecimal sourceAmount;
    private BigDecimal targetAmount;
    private LocalDateTime createTime;
}
