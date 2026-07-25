package com.scott.payment.payment.service;

import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.RefundPreparationResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundChannelResultTransactionService
 * @date : 2026-07-24 00:00
 * @description : Refund 渠道结果事务服务，负责在独立事务中保存渠道结果并通过 CAS 推进退款状态。
 * @status : create
 */
public interface RefundChannelResultTransactionService {

    /**
     * 保存 Refund 渠道同步结果。
     *
     * @param preparationResultDTO 已提交的 Refund 准备结果
     * @param invokeResultDTO 渠道调用结果
     */
    void recordRefundChannelResult(RefundPreparationResultDTO preparationResultDTO,
                                   PaymentChannelInvokeResultDTO invokeResultDTO);
}
