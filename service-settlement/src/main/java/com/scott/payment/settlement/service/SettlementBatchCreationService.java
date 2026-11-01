package com.scott.payment.settlement.service;

import com.scott.payment.settlement.dto.SettlementBatchCreateCommand;
import com.scott.payment.settlement.dto.SettlementBatchCreateResult;

/**
 * 结算批次创建领域服务边界，保证数据库发号和批次插入处于同一主库事务。
 */
public interface SettlementBatchCreationService {

    /**
     * 幂等创建结算批次。
     *
     * @param command 冻结批次身份和候选窗口
     * @return 新建或 create_request_key 复用的批次
     */
    SettlementBatchCreateResult create(SettlementBatchCreateCommand command);
}
