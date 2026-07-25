package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.dto.IncrementalAuthorizationPreparationResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IncrementalAuthorizationTransactionPreparationService
 * @date : 2026-07-24 00:00
 * @description : Incremental Authorization 本地准备事务服务，负责在渠道调用前提交幂等、动作事实和渠道请求 INIT。
 * @status : create
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
