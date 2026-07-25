package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.dto.CapturePreparationResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CaptureTransactionPreparationService
 * @date : 2026-07-24 00:00
 * @description : Capture 本地准备事务服务，负责在渠道调用前提交幂等、动作事实和渠道请求 INIT。
 * @status : create
 */
public interface CaptureTransactionPreparationService {

    /**
     * 准备 Capture 本地事实。
     *
     * @param commandDTO Capture 命令
     * @param idempotencyKey Capture 动作幂等键
     * @return Capture 准备结果
     */
    CapturePreparationResultDTO prepareCapture(PaymentCreateCommandDTO commandDTO, String idempotencyKey);
}
