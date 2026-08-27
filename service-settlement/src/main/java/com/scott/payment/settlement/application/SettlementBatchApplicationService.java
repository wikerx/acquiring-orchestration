package com.scott.payment.settlement.application;

import com.scott.payment.settlement.dto.SettlementBatchCreateCommand;
import com.scott.payment.settlement.dto.SettlementBatchCreateResult;
import com.scott.payment.settlement.dto.SettlementCandidateClaimCommand;
import com.scott.payment.settlement.dto.SettlementCandidateClaimResult;
import com.scott.payment.settlement.service.SettlementBatchCreationService;
import com.scott.payment.settlement.service.SettlementCandidateClaimService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchApplicationService
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 结算批次用例编排入口；只调用明确领域服务，不直接访问 Mapper 或执行金额和汇率计算。
 * @status : create
 */
@Service
public class SettlementBatchApplicationService {

    private final SettlementBatchCreationService batchCreationService;
    private final SettlementCandidateClaimService candidateClaimService;

    /**
     * 创建结算批次应用服务。
     *
     * @param batchCreationService 批次创建领域服务
     * @param candidateClaimService 候选认领领域服务
     */
    public SettlementBatchApplicationService(SettlementBatchCreationService batchCreationService,
                                             SettlementCandidateClaimService candidateClaimService) {
        this.batchCreationService = batchCreationService;
        this.candidateClaimService = candidateClaimService;
    }

    /**
     * 幂等创建一个结算批次。
     *
     * @param command 批次冻结身份和候选窗口
     * @return 新建或复用批次
     */
    public SettlementBatchCreateResult createBatch(SettlementBatchCreateCommand command) {
        return batchCreationService.create(command);
    }

    /**
     * 把一个真实清分候选独占认领到指定批次。
     *
     * @param command 批次、候选、预期版本和时间
     * @return 首次认领或幂等重试结果
     */
    public SettlementCandidateClaimResult claimCandidate(SettlementCandidateClaimCommand command) {
        return candidateClaimService.claim(command);
    }
}
