package com.scott.payment.clearing.entity;

import lombok.Data;

import java.math.BigDecimal;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingReserveRemainingMetricsDO
 * @date : 2026-08-27 19:46
 * @email : scott_x@163.com
 * @description : 单季度标签币种保证金剩余负债聚合投影，仅用于运维容量监控。
 * @status : update
 */
@Data
public class ClearingReserveRemainingMetricsDO {

    /** 原支付标签币种，也是保证金负债币种。 */
    private String reserveCurrency;

    /** 当前季度该币种所有保证金状态的剩余金额合计。 */
    private BigDecimal remainingAmount;
}
