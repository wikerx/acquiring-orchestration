package com.scott.payment.settlement.service;

import com.scott.payment.settlement.dto.SettlementCandidateClaimCommand;
import com.scott.payment.settlement.dto.SettlementCandidateClaimResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementCandidateClaimService
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 结算候选认领领域服务边界，负责影子隔离、依赖核验、数据库 CAS 和审计关系原子提交。
 * @status : create
 */
public interface SettlementCandidateClaimService {

    /**
     * 把单个真实 READY 候选独占认领到指定批次。
     *
     * @param command 批次、候选、预期版本和认领时间
     * @return 首次认领或同批幂等重试结果
     */
    SettlementCandidateClaimResult claim(SettlementCandidateClaimCommand command);
}
