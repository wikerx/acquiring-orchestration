package com.scott.payment.settlement.service;

import com.scott.payment.settlement.dto.SettlementBatchFacts;
import com.scott.payment.settlement.entity.SettlementBatchDO;
import com.scott.payment.settlement.entity.SettlementReviewOrderDO;

import java.util.List;
import com.scott.payment.settlement.entity.SettlementCandidateDO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : SettlementClearingFactService
 * @date : 2026-08-26 23:10
 * @email : scott_x@163.com
 * @description : 批量加载并校验一个结算批次的全部清分事实，不读取费用配置、Redis 或结算余额。
 * @status : create
 */
public interface SettlementClearingFactService {

    /**
     * 加载本批完整事实并构造币种集合。
     *
     * @param batch 已取得数据库处理租约的批次
     * @return 已完成一致性校验的批次事实
     */
    SettlementBatchFacts load(SettlementBatchDO batch);

    /** 加载并校验一个预审单独占候选的完整清分事实。 */
    SettlementBatchFacts loadReview(SettlementReviewOrderDO reviewOrder);

    /** 提交事务中在关系表写入前校验已经 CAS 锁定的候选事实。 */
    SettlementBatchFacts loadReviewSelection(SettlementReviewOrderDO reviewOrder,
                                             List<SettlementCandidateDO> candidates);
}
