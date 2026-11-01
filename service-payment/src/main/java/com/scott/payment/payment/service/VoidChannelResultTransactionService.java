package com.scott.payment.payment.service;

import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.VoidPreparationResultDTO;

/**
 * Void 渠道结果事务服务，负责在独立事务中保存渠道结果并通过 CAS 推进撤销动作。
 */
public interface VoidChannelResultTransactionService {

    /**
     * 保存 Void 渠道同步结果。
     *
     * @param preparationResultDTO 已提交的 Void 准备结果
     * @param invokeResultDTO 渠道调用结果
     * @return true 表示同步结果推进状态；false 表示回调或其他并发流程已先完成
     */
    boolean recordVoidChannelResult(VoidPreparationResultDTO preparationResultDTO,
                                    PaymentChannelInvokeResultDTO invokeResultDTO);
}
