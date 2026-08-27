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
 * @classname : SettlementBatchRateDO
 * @date : 2026-08-26 23:20
 * @email : scott_x@163.com
 * @description : 批次不可变汇率实体；每个原币种仅允许一条到目标币种的锁定直接汇率，禁止更新和删除。
 * @status : create
 */
@Data
@TableName("settlement_batch_rate")
public class SettlementBatchRateDO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String settlementBatchNo;
    private String sourceCurrency;
    private String targetCurrency;
    private String rateType;
    private BigDecimal directRate;
    private Integer sourceCurrencyExponent;
    private Integer targetCurrencyExponent;
    private String rateSource;
    private String quoteId;
    private String sourceQuoteDirection;
    private LocalDateTime effectiveTime;
    private LocalDateTime lockedTime;
    private String lockedBy;
    private String rateStatus;
    private LocalDateTime createTime;
}
