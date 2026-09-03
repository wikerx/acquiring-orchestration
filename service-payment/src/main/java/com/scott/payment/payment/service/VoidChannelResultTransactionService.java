package com.scott.payment.payment.service;

import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;
import com.scott.payment.payment.service.dto.VoidPreparationResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : VoidChannelResultTransactionService
 * @date : 2026-07-24 00:00
 * @email : scott_x@163.com
 * @description : Void 渠道结果事务服务，负责在独立事务中保存渠道结果并通过 CAS 推进撤销动作。
 * @status : create
 */
public interface VoidChannelResultTransactionService {

    /**
     * 保存 Void 渠道同步结果。
     *
     * @param preparationResultDTO 已提交的 Void 准备结果
     * @param invokeResultDTO 渠道调用结果
     */
    void recordVoidChannelResult(VoidPreparationResultDTO preparationResultDTO,
                                 PaymentChannelInvokeResultDTO invokeResultDTO);
}
