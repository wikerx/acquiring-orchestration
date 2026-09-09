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
 * @classname : SettlementReviewSummaryDO
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 预审审批页的不可变金额汇总；按支付、交易、结果项、费用类别、方向和币种分组，禁止跨币种直接相加。
 * @status : create
 */
@Data
@TableName("settlement_review_summary")
public class SettlementReviewSummaryDO {
    /** 汇总行数据库主键，插入前允许为空。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属结算预审单号。 */
    private String reviewOrderNo;
    /** 汇总所属平台商户号。 */
    private String merchantId;
    /** 平台支付类型；非交易保证金行允许使用约定占位值。 */
    private String paymentType;
    /** 平台支付方式；非交易保证金行允许使用约定占位值。 */
    private String paymentMethod;
    /** 平台交易类型；纯保证金行不伪造交易类型。 */
    private String transactionType;
    /** PRINCIPAL、FEE、RESERVE 或 NET_POSTING 等结果项类型。 */
    private String resultItemType;
    /** 费用业务类别；非费用结果项允许为空。 */
    private String feeCategory;
    /** CREDIT 或 DEBIT，金额字段本身保持非负。 */
    private String direction;
    /** 汇总原金额 ISO 币种。 */
    private String sourceCurrency;
    /** 汇总换算后目标 ISO 币种。 */
    private String targetCurrency;
    /** 去重后的交易或候选数量。 */
    private Long transactionCount;
    /** 原币种汇总金额，禁止与其他 sourceCurrency 混加。 */
    private BigDecimal sourceAmount;
    /** 统一目标币种汇总金额，已按目标 exponent 舍入。 */
    private BigDecimal targetAmount;
    /** 汇总快照创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
}
