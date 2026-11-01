package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.RiskTimelineEventResponse;

import java.util.List;

/**
 * 管理端交易风控时间轴只读查询服务。
 */
public interface AdminRiskTimelineQueryService {

    /**
     * 按平台交易号查询风控实际执行节点。
     *
     * @param paymentOrderNo 平台交易号
     * @return 按风控优先级排列的执行节点；未完成异步审计时返回空集合
     */
    List<RiskTimelineEventResponse> findRiskEvents(String paymentOrderNo);
}
