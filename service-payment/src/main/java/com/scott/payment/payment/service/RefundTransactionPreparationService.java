package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.dto.RefundPreparationResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundTransactionPreparationService
 * @date : 2026-07-24 00:00
 * @email : scott_x@163.com
 * @description : Refund 本地准备事务服务，负责在渠道调用前提交幂等、退款动作事实和渠道请求 INIT。
 * @status : create
 */
public interface RefundTransactionPreparationService {

    /**
     * 准备 Refund 本地事实。
     *
     * @param commandDTO Refund 命令
     * @param idempotencyKey Refund 动作幂等键
     * @return Refund 准备结果
     */
    RefundPreparationResultDTO prepareRefund(PaymentCreateCommandDTO commandDTO, String idempotencyKey);
}
