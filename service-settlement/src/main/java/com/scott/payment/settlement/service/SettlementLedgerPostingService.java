package com.scott.payment.settlement.service;

import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.entity.SettlementBatchDO;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementLedgerPostingService
 * @date : 2026-09-01 00:00
 * @email : scott_x@163.com
 * @description : 定义结算资金提交边界；唯一允许正式批次修改商户可用余额、追加资金流水并资金化保证金动作的领域服务。
 * @status : create
 */
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
