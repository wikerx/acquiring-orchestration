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
    /**
     * CLAIMED 枚举值，表示当前枚举定义中的一个受控业务取值。
     * <p>
     * 单位：无；格式：枚举常量；非敏感字段；不允许在业务状态流转中使用未声明取值。
     * </p>
     */
    CLAIMED,
    ALREADY_CLAIMED
}
