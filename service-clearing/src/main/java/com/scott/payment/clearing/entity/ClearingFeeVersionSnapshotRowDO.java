package com.scott.payment.clearing.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingFeeVersionSnapshotRowDO
 * @date : 2026-08-26 09:55
 * @email : scott_x@163.com
 * @description : 清分按明确不可变费用版本一次 JOIN 得到的只读扁平行，不拥有费用模板或商户配置写权限。
 * @status : create
 */
@Data
public class ClearingFeeVersionSnapshotRowDO {

    /** 费用配置所属平台商户号。 */
    private String merchantId;
    /** 费用方案 ID。 */
    private Long feePlanId;
    /** 不可变费用方案版本 ID。 */
    private Long feePlanVersionId;
    /** 运营可读费用版本号。 */
    private Integer feePlanVersionNo;
    /** 档案目标结算币种；清分阶段不据此换汇。 */
    private String settlementCurrency;
    /** 标签币种保证金比例。 */
    private BigDecimal reserveRate;
    /** 保证金留存周期单位。 */
    private String reserveDelayUnit;
    /** 保证金留存天数。 */
    private Integer reserveDelayDays;
    /** 原子费用规则 ID。 */
    private Long feeRuleId;
    /** 费用类别稳定编码。 */
    private String feeCategory;
    /** 规则适用的平台统一交易类型。 */
    private String transactionType;
    /** 规则适用支付类型。 */
    private String paymentType;
    /** 规则适用支付方式或品牌。 */
    private String paymentMethod;
    /** 规则适用风险服务类型；非风险费时为空。 */
    private String riskServiceType;
    /** 费用触发时点。 */
    private String chargeTrigger;
    /** 费用计算模式。 */
    private String feeMode;
    /** 标签金额百分比费率。 */
    private BigDecimal percentageRate;
    /** 固定单笔费，币种固定为 USD。 */
    private BigDecimal fixedAmountUsd;
    /** 最低收费限制，币种固定为 USD；未配置时为空。 */
    private BigDecimal minimumAmountUsd;
    /** 最高收费限制，币种固定为 USD；未配置时为空。 */
    private BigDecimal maximumAmountUsd;
    /** 阶梯累计指标 COUNT 或 AMOUNT；非阶梯规则时为空。 */
    private String tierMetric;
    /** 阶梯行 ID；非阶梯规则时为空。 */
    private Long feeTierId;
    /** 阶梯下界；COUNT 为笔数，AMOUNT 为 USD 主单位。 */
    private BigDecimal tierLowerBound;
    /** 阶梯上界；无上界时为空。 */
    private BigDecimal tierUpperBound;
    /** 当前阶梯标签金额百分比费率。 */
    private BigDecimal tierPercentageRate;
    /** 当前阶梯固定单笔费，币种固定为 USD。 */
    private BigDecimal tierFixedAmountUsd;
    /** 当前阶梯最低收费限制，币种固定为 USD。 */
    private BigDecimal tierMinimumAmountUsd;
    /** 当前阶梯最高收费限制，币种固定为 USD。 */
    private BigDecimal tierMaximumAmountUsd;
}
