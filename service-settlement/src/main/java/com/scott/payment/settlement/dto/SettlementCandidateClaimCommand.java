package com.scott.payment.settlement.dto;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementCandidateClaimCommand
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 单个结算候选认领内部命令，携带批次、候选版本和统一认领时间用于数据库 CAS。
 * @status : create
 * @param settlementBatchNo 目标结算批次号
 * @param candidateId 清分修订级候选主键
 * @param expectedCandidateVersion 调用方读取到的候选版本
 * @param claimedTime 本次认领统一时间
 */
public record SettlementCandidateClaimCommand(String settlementBatchNo,
                                              Long candidateId,
                                              Long expectedCandidateVersion,
                                              LocalDateTime claimedTime) {

    public SettlementCandidateClaimCommand {
        if (settlementBatchNo == null || settlementBatchNo.isBlank()) {
            throw new IllegalArgumentException("settlement batch number is required");
        }
        settlementBatchNo = settlementBatchNo.trim();
        if (candidateId == null || candidateId <= 0) {
            throw new IllegalArgumentException("candidate id must be positive");
        }
        if (expectedCandidateVersion == null || expectedCandidateVersion < 0) {
            throw new IllegalArgumentException("expected candidate version is invalid");
        }
        Objects.requireNonNull(claimedTime, "claimed time is required");
    }
}
