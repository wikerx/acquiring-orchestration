package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.domain.state.PaymentTransactionTypeEnum;
import com.scott.payment.payment.service.dto.CapturePreparationResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CaptureTransactionPreparationService
 * @date : 2026-07-24 00:00
 * @email : scott_x@163.com
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

    /**
     * 准备 Capture 类本地事实。
     * <p>
     * 预授权完成与请款共用渠道 Capture 能力，但动作事实和幂等类型必须保留真实交易类型。
     *
     * @param commandDTO Capture 类命令
     * @param idempotencyKey 动作幂等键
     * @param transactionType Capture 类交易类型
     * @return Capture 类准备结果
     */
    default CapturePreparationResultDTO prepareCapture(PaymentCreateCommandDTO commandDTO,
                                                       String idempotencyKey,
                                                       PaymentTransactionTypeEnum transactionType) {
        return prepareCapture(commandDTO, idempotencyKey);
    }
}
