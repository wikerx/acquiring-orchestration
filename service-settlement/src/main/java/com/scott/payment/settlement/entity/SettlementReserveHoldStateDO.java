package com.scott.payment.settlement.entity;

import lombok.Data;
import java.time.LocalDateTime;
import java.math.BigDecimal;

/**
 * 只读清分保证金生命周期投影，不允许结算侧重置或推进清分状态。
 */
@Data
public class SettlementReserveHoldStateDO {
    /** 清分保证金状态业务号。 */
    private String reserveStateId;
    /** 原支付号。 */
    private String originalTransactionId;
    /** 原支付精确分片时间。 */
    private LocalDateTime transactionDateTime;
    /** 原支付财务状态号。 */
    private String originalFinanceStateId;
    /** 当前生效的 HOLD 引用。 */
    private String originalHoldDetailNo;
    /** 状态所属商户。 */
    private String merchantId;
    /** 原标签币种。 */
    private String reserveCurrency;
    /** 原标签币种精度。 */
    private Integer reserveCurrencyExponent;
    /** 原扣留累计额。 */
    private BigDecimal retainedAmount;
    /** 已返还累计额。 */
    private BigDecimal returnedAmount;
    /** 清分已生成释放累计额。 */
    private BigDecimal releasedAmount;
    /** 借方调整累计额。 */
    private BigDecimal debitAdjustmentAmount;
    /** 贷方调整累计额。 */
    private BigDecimal creditAdjustmentAmount;
    /** 清分剩余责任。 */
    private BigDecimal remainingAmount;
    /** 清分生命周期状态。 */
    private String reserveStatus;
    /** 当前状态版本。 */
    private Long version;
}
