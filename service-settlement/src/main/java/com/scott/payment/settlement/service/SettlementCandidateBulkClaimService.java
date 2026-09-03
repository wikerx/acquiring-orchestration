package com.scott.payment.settlement.service;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementCandidateBulkClaimService
 * @date : 2026-08-26 22:10
 * @email : scott_x@163.com
 * @description : 自动批次的有界候选批量认领和封批边界；每页使用数据库锁、版本 CAS 与不可删除关系表。
 * @status : create
 */
public interface SettlementCandidateBulkClaimService {

    /**
     * 分页认领当前切点前的可用候选，并在无更多独立候选时把非空批次封为 CLAIMED。
     *
     * @param settlementBatchNo 目标批次号
     * @param claimedTime 统一认领审计时间
     * @return 批次封批时持有的候选总数；返回 0 表示并发后已无候选可认领
     */
    int claimAndSeal(String settlementBatchNo, LocalDateTime claimedTime);
}
