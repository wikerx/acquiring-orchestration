package com.scott.payment.payment.service;

import com.scott.payment.payment.service.dto.CapturePreparationResultDTO;
import com.scott.payment.payment.service.dto.PaymentChannelInvokeResultDTO;

/**
 * Capture 渠道结果事务服务，负责在独立事务中保存渠道结果并通过 CAS 推进状态。
 */
public interface CaptureChannelResultTransactionService {

    /**
     * 保存 Capture 渠道同步结果。
     *
     * @param preparationResultDTO 已提交的 Capture 准备结果
     * @param invokeResultDTO 渠道调用结果
     * @return true 表示同步结果推进状态；false 表示回调或其他并发流程已先完成
     */
    boolean recordCaptureChannelResult(CapturePreparationResultDTO preparationResultDTO,
                                       PaymentChannelInvokeResultDTO invokeResultDTO);
}
