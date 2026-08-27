package com.scott.payment.settlement.service;

import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.entity.SettlementBatchDO;

import java.time.LocalDateTime;

/** 结算资金提交边界；唯一允许由结算批次修改商户余额、资金流水和保证金聚合的服务。 */
public interface SettlementLedgerPostingService {

    /**
     * 原子提交一个已计算批次。
     *
     * @return 本批提交的候选数量
     */
    int post(SettlementBatchDO leasedBatch,
             SettlementBatchFacts facts,
             String owner,
             LocalDateTime now);
}
