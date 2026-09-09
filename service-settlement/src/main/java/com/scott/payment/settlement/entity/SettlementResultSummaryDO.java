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
    /** 结果汇总数据库主键，插入前允许为空。 */
    @TableId(type = IdType.AUTO)
    private Long id;
    /** 所属正式结算批次号。 */
    private String settlementBatchNo;
    /** 汇总所属平台商户号。 */
    private String merchantId;
    /** 平台支付类型维度；非交易结果使用约定值。 */
    private String paymentType;
    /** 平台支付方式维度；非交易结果使用约定值。 */
    private String paymentMethod;
    /** 平台交易类型维度；纯保证金结果不伪造交易类型。 */
    private String transactionType;
    /** PRINCIPAL、FEE、RESERVE 或 ADJUSTMENT 等结果项类型。 */
    private String resultItemType;
    /** 费用类别；非费用汇总允许为空。 */
    private String feeCategory;
    /** CREDIT 或 DEBIT，金额本身保持非负。 */
    private String direction;
    /** 汇总原金额 ISO 币种。 */
    private String sourceCurrency;
    /** 汇总目标 ISO 结算币种。 */
    private String targetCurrency;
    /** 去重后的候选或交易数量。 */
    private Long transactionCount;
    /** 同 sourceCurrency 原金额合计，禁止跨币种相加。 */
    private BigDecimal sourceAmount;
    /** 同 targetCurrency 舍入后目标金额合计。 */
    private BigDecimal targetAmount;
    /** 汇总创建时间，数据库精度为毫秒。 */
    private LocalDateTime createTime;
}
