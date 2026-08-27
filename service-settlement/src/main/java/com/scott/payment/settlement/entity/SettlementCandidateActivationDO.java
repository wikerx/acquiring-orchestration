package com.scott.payment.settlement.entity;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementCandidateActivationDO
 * @date : 2026-08-26 22:00
 * @email : scott_x@163.com
 * @description : 候选、唯一活动结算档案和正常资金账户联表锁读投影，用于激活前执行完整身份校验。
 * @status : create
 */
@Data
public class SettlementCandidateActivationDO {
    /** 待激活候选主键。 */
    private Long candidateId;
    /** 候选 shadow 状态 CAS 版本。 */
    private Long candidateVersion;
    /** 候选与档案共同商户号。 */
    private String merchantId;
    /** 清分输出的目标币种。 */
    private String candidateTargetCurrency;
    /** 清分输出的目标币种 exponent。 */
    private Integer candidateTargetCurrencyExponent;
    /** 候选最早结算日期。 */
    private LocalDate settlementEligibleDate;
    /** 本次冻结的活动档案主键。 */
    private Long settlementProfileId;
    /** 档案绑定且已校验正常的资金账户主键。 */
    private Long settlementAccountId;
    /** 档案目标币种。 */
    private String profileTargetCurrency;
    /** 档案目标币种 exponent。 */
    private Integer profileTargetCurrencyExponent;
    /** 档案业务时区。 */
    private String businessTimeZone;
    /** 档案每日日切时间。 */
    private LocalTime dailyCutoffTime;
}
