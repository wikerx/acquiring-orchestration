package com.scott.payment.settlement.service;

import java.time.LocalDateTime;

/**
 * 真实候选激活边界，只冻结唯一活动结算档案，不创建批次、不读取汇率且不处理余额。
 */
public interface SettlementCandidateActivationService {

    /**
     * 锁定并批量激活一页合法影子候选。
     *
     * @param limit 单次最大候选数，范围 1 至 200
     * @param activatedTime 统一激活审计时间
     * @return 成功切换为真实候选的数量
     */
    int activateEligibleCandidates(int limit, LocalDateTime activatedTime);
}
