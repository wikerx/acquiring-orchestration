package com.scott.payment.clearing.service;

import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingCommandResponse;
import com.scott.payment.clearing.api.internal.dto.ClearingManagementDTOs.ClearingRecalculateRequest;

/**
 * 未结算动作清分重算边界；实现必须保留旧修订并原子切换结算候选。
 */
public interface ClearingRecalculationService {

    /**
     * 使用指定的不可变费用版本重算未结算动作，事务内追加新修订并替换未认领候选。
     *
     * @param transactionId 动作交易号
     * @param request 包含真实分片时间、当前修订、预期版本和目标费用版本的命令
     * @return 新修订及其权威清分状态
     * @throws IllegalArgumentException 命令字段不完整时抛出
     * @throws IllegalStateException 动作已结算、版本过期或关联事实不一致时抛出
     */
    ClearingCommandResponse recalculate(String transactionId, ClearingRecalculateRequest request);
}
