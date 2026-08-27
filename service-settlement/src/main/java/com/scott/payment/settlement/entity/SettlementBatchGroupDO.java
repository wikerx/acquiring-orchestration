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
 * @description : 真实 READY 候选按冻结档案、账户和目标币种聚合的自动建批投影，不包含清分金额。
 * @status : create
 */
@Data
public class SettlementBatchGroupDO {
    /** REGULAR 或 RESERVE_RELEASE，由候选来源确定。 */
    private String batchType;
    /** 当前结算维度最早 READY 候选主键，用于生成有界批次的稳定请求键。 */
    private Long anchorCandidateId;
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
    /** 当前未归属候选中最早结算日期。 */
    private LocalDate earliestEligibleDate;
}
