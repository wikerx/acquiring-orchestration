package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.dto.VoidPreparationResultDTO;

/**
 * Void 本地准备事务服务，负责在渠道 Void / Authorization Cancel 调用前提交幂等、动作事实和渠道请求 INIT。
 */
public interface VoidTransactionPreparationService {

    /**
     * 准备 Void 本地事实。
     *
     * @param commandDTO Void 命令
     * @param idempotencyKey Void 动作幂等键
     * @return Void 准备结果
     */
    VoidPreparationResultDTO prepareVoid(PaymentCreateCommandDTO commandDTO, String idempotencyKey);
}
