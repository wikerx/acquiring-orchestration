package com.scott.payment.settlement.entity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import java.math.BigDecimal;

/**
 * 只读 HOLD 完整资金及规则快照，支持证明新修订仅复制了原责任。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SettlementReserveHoldSnapshotDO extends SettlementReserveClearingDetailDO {
    /** 原计提基数。 */
    private BigDecimal basisAmount;
    /** 原计提费率。 */
    private BigDecimal reserveRate;
    /** HOLD 冻结剩余额。 */
    private BigDecimal remainingAmount;
    /** 费用方案主键。 */
    private Long feePlanId;
    /** 费用方案版本主键。 */
    private Long feePlanVersionId;
    /** 费用方案版本号。 */
    private Integer feePlanVersionNo;
    /** 冻结保证金规则哈希。 */
    private String reserveSnapshotHash;
    /** 冻结计提基数类型。 */
    private String reserveBasis;
    /** 冻结延迟单位。 */
    private String reserveDelayUnit;
    /** 冻结延迟天数。 */
    private Integer reserveDelayDays;
}
