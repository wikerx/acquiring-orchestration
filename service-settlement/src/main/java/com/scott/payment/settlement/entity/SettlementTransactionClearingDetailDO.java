package com.scott.payment.settlement.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementTransactionClearingDetailDO
 * @date : 2026-08-26 23:10
 * @email : scott_x@163.com
 * @description : 结算侧只读交易清分事实投影；保留原币种组件、USD 限额及支付分组快照，不重新计算清分费用。
 * @status : create
 */
@Data
public class SettlementTransactionClearingDetailDO {
    /** 清分交易明细数据库主键。 */
    private Long id;
    /** 清分明细稳定业务号。 */
    private String clearingDetailNo;
    /** 清分幂等财务状态标识。 */
    private String financeStateId;
    /** 当前清分动作所属平台交易号。 */
    private String transactionId;
    /** 当前清分动作单号。 */
    private String operationId;
    /** 本金或退款返费追溯的来源交易号。 */
    private String sourceTransactionId;
    /** 清分事实所属平台商户号。 */
    private String merchantId;
    /** 清分时冻结的平台支付类型。 */
    private String paymentType;
    /** 清分时冻结的平台支付方式。 */
    private String paymentMethod;
    /** 清分时冻结的平台交易类型。 */
    private String transactionType;
    /** 清分修订号，结算候选必须精确匹配。 */
    private Integer clearingRevision;
    /** 同一清分修订内的确定性明细行号。 */
    private Integer lineNo;
    /** PRINCIPAL 或 FEE_COMPONENT 等清分项类型。 */
    private String itemType;
    /** 费用类别；本金行允许为空。 */
    private String feeCategory;
    /** CREDIT 或 DEBIT，amount 本身保持非负。 */
    private String direction;
    /** 百分比费计算所用标签 ISO 币种。 */
    private String labelCurrency;
    /** 百分比费计算所用标签非负金额。 */
    private BigDecimal labelAmount;
    /** 标签币种 ISO 小数位。 */
    private Integer labelCurrencyExponent;
    /** 需要合并应用最低/最高限额的费用组号。 */
    private String feeGroupNo;
    /** 费用组内组件序号；本金行允许为空。 */
    private Integer componentNo;
    /** PERCENTAGE、FIXED 或其他清分费用组件类型。 */
    private String componentType;
    /** 原清分项非负金额，尚未按结算汇率换算。 */
    private BigDecimal amount;
    /** amount 的 ISO 币种；百分比组件为标签币种，固定费/限额保持 USD。 */
    private String currency;
    /** amount 币种 ISO 小数位。 */
    private Integer currencyExponent;
    /** 固定以 USD 表示的费用组最低限额；无配置时为空。 */
    private BigDecimal minimumAmountUsd;
    /** 固定以 USD 表示的费用组最高限额；无配置时为空。 */
    private BigDecimal maximumAmountUsd;
    /** 清分阶段限额评估状态，结算据此判断是否需要跨币种归并。 */
    private String limitEvaluationStatus;
    /** 清分已应用的限额结果；待结算评估时允许为空。 */
    private String appliedLimit;
    /** 清分冻结舍入模式。 */
    private String roundingMode;
    /** 清分公式和配置版本审计快照。 */
    private String formulaSnapshot;
    /** 清分事实有效状态，结算只读取有效终态。 */
    private String recordStatus;
    /** 当前交易动作分片时间，定位物理表时不允许为空。 */
    private LocalDateTime transactionDateTime;
}
