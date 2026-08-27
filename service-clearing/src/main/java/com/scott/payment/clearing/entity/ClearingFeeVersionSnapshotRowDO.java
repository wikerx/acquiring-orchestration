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

    private String merchantId;
    private Long feePlanId;
    private Long feePlanVersionId;
    private Integer feePlanVersionNo;
    private String settlementCurrency;
    private BigDecimal reserveRate;
    private String reserveDelayUnit;
    private Integer reserveDelayDays;
    private Long feeRuleId;
    private String feeCategory;
    private String transactionType;
    private String paymentType;
    private String paymentMethod;
    private String riskServiceType;
    private String chargeTrigger;
    private String feeMode;
    private BigDecimal percentageRate;
    private BigDecimal fixedAmountUsd;
    private BigDecimal minimumAmountUsd;
    private BigDecimal maximumAmountUsd;
    private String tierMetric;
    private Long feeTierId;
    private BigDecimal tierLowerBound;
    private BigDecimal tierUpperBound;
    private BigDecimal tierPercentageRate;
    private BigDecimal tierFixedAmountUsd;
    private BigDecimal tierMinimumAmountUsd;
    private BigDecimal tierMaximumAmountUsd;
}
