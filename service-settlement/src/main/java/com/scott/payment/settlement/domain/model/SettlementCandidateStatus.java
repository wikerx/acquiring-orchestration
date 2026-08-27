package com.scott.payment.settlement.domain.model;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementCandidateStatus
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 清分修订级结算候选状态；候选只能从 READY 通过版本 CAS 进入 CLAIMED。
 * @status : create
 */
public enum SettlementCandidateStatus {
    READY,
    SUPERSEDED,
    CLAIMED,
    POSTED,
    MANUAL_REVIEW,
    CANCELLED
}
