package com.scott.payment.payment.service;

import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.RefundPreparationResultDTO;

/**
 * Refund 渠道结果事务服务，负责在独立事务中保存渠道结果并通过 CAS 推进退款状态。
 */
public interface RefundChannelResultTransactionService {

    /**
     * 保存 Refund 渠道同步结果。
     *
     * @param preparationResultDTO 已提交的 Refund 准备结果
     * @param invokeResultDTO 渠道调用结果
     * @return true 表示同步结果推进状态；false 表示回调或其他并发流程已先完成
     */
    boolean recordRefundChannelResult(RefundPreparationResultDTO preparationResultDTO,
                                      PaymentChannelInvokeResultDTO invokeResultDTO);
}
