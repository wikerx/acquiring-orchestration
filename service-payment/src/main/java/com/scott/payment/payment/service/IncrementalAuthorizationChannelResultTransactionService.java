package com.scott.payment.payment.service;

import com.scott.payment.payment.service.dto.IncrementalAuthorizationPreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IncrementalAuthorizationChannelResultTransactionService
 * @date : 2026-07-24 00:00
 * @email : scott_x@163.com
 * @description : Incremental Authorization 渠道结果事务服务，负责独立保存同步结果并 CAS 推进动作终态。
 * @status : create
 */
public interface IncrementalAuthorizationChannelResultTransactionService {

    /**
     * 保存 Incremental Authorization 渠道同步结果。
     *
     * @param preparationResultDTO 已提交的增量授权准备结果
     * @param invokeResultDTO 渠道调用结果
     */
    void recordIncrementalAuthorizationChannelResult(IncrementalAuthorizationPreparationResultDTO preparationResultDTO,
                                                     PaymentChannelInvokeResultDTO invokeResultDTO);
}
