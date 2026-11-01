package com.scott.payment.payment.service;

import com.scott.payment.payment.service.dto.IncrementalAuthorizationPreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;

/**
 * Incremental Authorization 渠道结果事务服务，负责独立保存同步结果并 CAS 推进动作终态。
 */
public interface IncrementalAuthorizationChannelResultTransactionService {

    /**
     * 保存 Incremental Authorization 渠道同步结果。
     *
     * @param preparationResultDTO 已提交的增量授权准备结果
     * @param invokeResultDTO 渠道调用结果
     * @return true 表示同步结果推进状态；false 表示回调或其他并发流程已先完成
     */
    boolean recordIncrementalAuthorizationChannelResult(
            IncrementalAuthorizationPreparationResultDTO preparationResultDTO,
            PaymentChannelInvokeResultDTO invokeResultDTO);
}
