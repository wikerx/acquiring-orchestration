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
 * @classname : SettlementReviewRateDO
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 预审单不可变统一汇率矩阵行；所有来源报价先归一为 sourceCurrency 乘 directRate 得到 targetCurrency，审批仅复用冻结矩阵。
 * @status : create
 */
@Data
@TableName("settlement_review_rate")
public class SettlementReviewRateDO {
    /** 汇率矩阵行数据库主键，插入前允许为空。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属结算预审单号。 */
    private String reviewOrderNo;
    /** 待换算金额的 ISO 来源币种。 */
    private String sourceCurrency;
    /** 预审统一目标 ISO 结算币种。 */
    private String targetCurrency;
    /** 归一直接汇率：source amount 乘此值等于 target amount，至少保留八位精度。 */
    private BigDecimal directRate;
    /** 来源币种 ISO 小数位。 */
    private Integer sourceCurrencyExponent;
    /** 目标币种 ISO 小数位。 */
    private Integer targetCurrencyExponent;
    /** 汇率提供方或内部来源编码。 */
    private String rateSource;
    /** 来源报价唯一标识，用于审计追溯。 */
    private String quoteId;
    /** 原始报价 DIRECT、INVERSE 或 IDENTITY 方向。 */
    private String sourceQuoteDirection;
    /** 原始报价生效时间，数据库精度为毫秒。 */
    private LocalDateTime effectiveTime;
    /** 预审冻结汇率时间，数据库精度为毫秒。 */
    private LocalDateTime lockedTime;
    /** 锁定主体标识，不包含令牌或密钥。 */
    private String lockedBy;
    /** 汇率矩阵行创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
}
