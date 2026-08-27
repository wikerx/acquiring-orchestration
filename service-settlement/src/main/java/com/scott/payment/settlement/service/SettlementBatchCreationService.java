package com.scott.payment.settlement.service;

import com.scott.payment.settlement.dto.SettlementBatchCreateCommand;
import com.scott.payment.settlement.dto.SettlementBatchCreateResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchCreationService
 * @date : 2026-08-26 20:00
 * @email : scott_x@163.com
 * @description : 结算批次创建领域服务边界，保证数据库发号和批次插入处于同一主库事务。
 * @status : create
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
