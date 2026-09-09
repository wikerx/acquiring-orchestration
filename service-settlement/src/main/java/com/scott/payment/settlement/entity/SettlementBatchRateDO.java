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
    /** 批次汇率行数据库主键，插入前允许为空。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属正式结算批次号。 */
    private String settlementBatchNo;
    /** 人工批次继承的预审汇率行 ID；自动批次为空。 */
    private Long reviewRateId;
    /** 待换算金额的 ISO 来源币种。 */
    private String sourceCurrency;
    /** 批次统一目标 ISO 结算币种。 */
    private String targetCurrency;
    /** DIRECT、INVERSE 归一结果或 IDENTITY。 */
    private String rateType;
    /** 归一直接汇率：source amount 乘此值等于 target amount，至少保留八位精度。 */
    private BigDecimal directRate;
    /** 来源币种 ISO 小数位。 */
    private Integer sourceCurrencyExponent;
    /** 目标币种 ISO 小数位。 */
    private Integer targetCurrencyExponent;
    /** 汇率提供方或内部来源编码。 */
    private String rateSource;
    /** 原始报价唯一标识，用于审计追溯。 */
    private String quoteId;
    /** 原始报价 DIRECT、INVERSE 或 IDENTITY 方向。 */
    private String sourceQuoteDirection;
    /** 原始报价生效时间，数据库精度为毫秒。 */
    private LocalDateTime effectiveTime;
    /** 批次锁定汇率时间，数据库精度为毫秒。 */
    private LocalDateTime lockedTime;
    /** 锁定主体标识，不包含令牌或密钥。 */
    private String lockedBy;
    /** LOCKED 等不可变汇率状态。 */
    private String rateStatus;
    /** 批次汇率行创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
}
