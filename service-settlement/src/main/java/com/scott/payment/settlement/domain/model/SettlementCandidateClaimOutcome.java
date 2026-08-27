package com.scott.payment.settlement.domain.model;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementCandidateClaimOutcome
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 候选认领命令结果，区分首次成功和同一批次安全重试。
 * @status : create
 */
public enum SettlementCandidateClaimOutcome {
    CLAIMED,
    ALREADY_CLAIMED
}
