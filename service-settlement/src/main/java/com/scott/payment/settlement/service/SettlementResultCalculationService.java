package com.scott.payment.settlement.service;

import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.dto.SettlementLockedRateMatrix;
import com.scott.payment.settlement.entity.SettlementBatchDO;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementResultCalculationService
 * @date : 2026-08-26 23:30
 * @email : scott_x@163.com
 * @description : 使用清分事实和批次统一汇率生成不可变结算结果及汇总，并推进到等待资金提交的 CALCULATED 状态。
 * @status : create
 */
public interface SettlementResultCalculationService {

    /**
     * 原子生成结果、回读校验汇总并完成 CALCULATED 状态 CAS。
     *
     * @param batch 已锁定汇率且持有租约的批次
     * @param facts 完整清分事实
     * @param rates 完整批次汇率矩阵
     * @param owner 当前租约所有者
     * @param now 计算时间
     * @return 持久化结果明细数
     */
    int calculateAndPersist(SettlementBatchDO batch,
                            SettlementBatchFacts facts,
                            SettlementLockedRateMatrix rates,
                            String owner,
                            LocalDateTime now);
}
