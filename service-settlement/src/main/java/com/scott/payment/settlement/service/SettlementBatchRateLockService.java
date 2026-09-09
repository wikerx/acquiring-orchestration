package com.scott.payment.settlement.service;

import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.dto.SettlementLockedRateMatrix;
import com.scott.payment.settlement.entity.SettlementBatchDO;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementBatchRateLockService
 * @date : 2026-08-26 23:20
 * @email : scott_x@163.com
 * @description : 结算批次完整汇率矩阵锁定边界；已有矩阵只允许完整复用，禁止部分补算和覆盖。
 * @status : create
 */
public interface SettlementBatchRateLockService {

    /**
     * 锁定或复用批次完整汇率矩阵，并把批次推进至 RATE_LOCKED。
     *
     * @param batch 已取得处理租约的批次
     * @param facts 本批全部清分事实
     * @param owner 当前租约所有者
     * @param now 统一锁定时间
     * @return 已回读验证的矩阵和数据库 ID
     */
    SettlementLockedRateMatrix lockOrLoad(SettlementBatchDO batch,
                                          SettlementBatchFacts facts,
                                          String owner,
                                          LocalDateTime now);
}
