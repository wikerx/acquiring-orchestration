package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.dto.IncrementalAuthorizationPreparationResultDTO;

/**
 * Incremental Authorization 本地准备事务服务，负责在渠道调用前提交幂等、动作事实和渠道请求 INIT。
 */
public interface IncrementalAuthorizationTransactionPreparationService {

    /**
     * 准备 Incremental Authorization 本地事实。
     *
     * @param commandDTO Incremental Authorization 命令
     * @param idempotencyKey 增量授权动作幂等键
     * @return Incremental Authorization 准备结果
     */
    IncrementalAuthorizationPreparationResultDTO prepareIncrementalAuthorization(PaymentCreateCommandDTO commandDTO,
                                                                                 String idempotencyKey);
}
