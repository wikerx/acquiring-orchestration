package com.scott.payment.payment.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantFeeVersionSnapshotRowDO
 * @date : 2026-08-25 22:40
 * @email : scott_x@163.com
 * @description : Payment 按不可变费用版本一次 JOIN 读取的扁平行投影，用于组装规则和阶梯快照，不拥有费用表写权限。
 * @status : create
 */
@Data
public class MerchantFeeVersionSnapshotRowDO {

    /** 费用版本所属平台商户号。 */
    private String merchantId;
    /** 商户 MERCHANT 费用方案主键。 */
    private Long feePlanId;
    /** 已生效或已被替代的不可变费用版本主键。 */
    private Long feePlanVersionId;
    /** 方案内不可复用版本号。 */
    private Integer feePlanVersionNo;
    /** 商户单一目标结算币种；清分阶段不得据此换汇。 */
    private String settlementCurrency;
    /** 标签金额保证金百分比，10 表示 10%。 */
    private BigDecimal reserveRate;
    /** 保证金留存周期单位，D 为自然日、T 为工作日。 */
    private String reserveDelayUnit;
    /** 保证金留存天数。 */
    private Integer reserveDelayDays;

    /** 原子费用规则主键。 */
    private Long feeRuleId;
    /** 费用分类，例如 TRANSACTION_FEE、REFUND_FEE 或 RISK_FEE。 */
    private String feeCategory;
    /** 规则匹配的交易动作类型。 */
    private String transactionType;
    /** 规则匹配的支付类型。 */
    private String paymentType;
    /** 规则匹配的支付方式或卡品牌。 */
    private String paymentMethod;
    /** INTERNAL、EXTERNAL、THREE_DS 或 NONE。 */
    private String riskServiceType;
    /** NO_CHARGE、SUCCESS、SUCCESS_OR_FAILURE、ON_CALL 或 NOT_APPLICABLE。 */
    private String chargeTrigger;
    /** STANDARD 或 TIER。 */
    private String feeMode;
    /** 按动作标签金额和标签币种计算的百分比数值。 */
    private BigDecimal percentageRate;
    /** 单笔固定费用，币种固定为 USD。 */
    private BigDecimal fixedAmountUsd;
    /** 最低费用限制，币种固定为 USD；未配置时为空。 */
    private BigDecimal minimumAmountUsd;
    /** 最高费用限制，币种固定为 USD；未配置时为空。 */
    private BigDecimal maximumAmountUsd;
    /** COUNT 或 AMOUNT；AMOUNT 按既有口径累计 USD。 */
    private String tierMetric;

    /** 阶梯主键；标准费率时为空。 */
    private Long feeTierId;
    /** 阶梯包含下界，COUNT 为笔数、AMOUNT 为 USD 累计金额。 */
    private BigDecimal tierLowerBound;
    /** 阶梯不包含上界；末档为空。 */
    private BigDecimal tierUpperBound;
    /** 当前阶梯百分比数值，仍按标签金额和标签币种计算。 */
    private BigDecimal tierPercentageRate;
    /** 当前阶梯固定费用，币种固定为 USD。 */
    private BigDecimal tierFixedAmountUsd;
    /** 当前阶梯最低费用限制，币种固定为 USD。 */
    private BigDecimal tierMinimumAmountUsd;
    /** 当前阶梯最高费用限制，币种固定为 USD。 */
    private BigDecimal tierMaximumAmountUsd;
}
