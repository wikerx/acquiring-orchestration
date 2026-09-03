package com.scott.payment.settlement.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchGroupDO
 * @date : 2026-08-26 22:10
 * @email : scott_x@163.com
 * @description : READY 候选按冻结档案、账户和目标币种聚合的自动扫描投影；不作为正式建批候选快照。
 * @status : create
 */
@Data
public class SettlementBatchGroupDO {
    /** REGULAR、RESERVE_RELEASE 或 ADJUSTMENT，由候选来源确定；非敏感且不允许为空。 */
    private String batchType;
    /** 候选冻结的结算档案 ID。 */
    private Long settlementProfileId;
    /** 平台商户号。 */
    private String merchantId;
    /** 目标资金账户 ID。 */
    private Long settlementAccountId;
    /** 目标结算 ISO 币种。 */
    private String targetCurrency;
    /** 目标币种 ISO exponent。 */
    private Integer targetCurrencyExponent;
    /** 商户结算日历 IANA 时区。 */
    private String businessTimeZone;
    /** 商户每日结算日切时间。 */
    private LocalTime dailyCutoffTime;
    /** AUTO_POST 或 AUTO_REVIEW；MANUAL 档案不会进入自动分组。 */
    private String processingMode;
    /** 当前未归属候选中最早结算日期。 */
    private LocalDate earliestEligibleDate;
}
