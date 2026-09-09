package com.scott.payment.settlement.dto;

import com.scott.payment.settlement.domain.model.SettlementCandidateClaimOutcome;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementCandidateClaimResult
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 候选认领结果，返回稳定批次候选关系号并区分首次认领与幂等重试。
 * @status : create
 * @param outcome 认领结果类型
 * @param settlementBatchNo 目标结算批次号
 * @param candidateId 候选主键
 * @param batchCandidateNo 稳定批次候选关系号
 */
public record SettlementCandidateClaimResult(SettlementCandidateClaimOutcome outcome,
                                             String settlementBatchNo,
                                             Long candidateId,
                                             String batchCandidateNo) {
}
