package com.scott.payment.payment.service;

import com.scott.payment.payment.service.dto.CapturePreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CaptureChannelResultTransactionService
 * @date : 2026-07-24 00:00
 * @email : scott_x@163.com
 * @description : Capture 渠道结果事务服务，负责在独立事务中保存渠道结果并通过 CAS 推进状态。
 * @status : create
 */
public interface CaptureChannelResultTransactionService {

    /**
     * 保存 Capture 渠道同步结果。
     *
     * @param preparationResultDTO 已提交的 Capture 准备结果
     * @param invokeResultDTO 渠道调用结果
     */
    void recordCaptureChannelResult(CapturePreparationResultDTO preparationResultDTO,
                                    PaymentChannelInvokeResultDTO invokeResultDTO);
}
