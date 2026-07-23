package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.service.dto.PaymentInitialPreparationResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionPreparationService
 * @date : 2026-07-23 00:00
 * @email : scott_x@163.com
 * @description : 首次交易本地准备服务，位于 service-payment 服务层，负责在渠道调用前提交交易事实、幂等结果、路由结果和渠道请求 INIT。
 * @status : create
 */
public interface PaymentTransactionPreparationService {

    /**
     * 准备首次 Payment/Auth/PreAuth 本地事实。
     * <p>
     * 调用方必须在该方法返回后、事务提交完成后，才允许使用返回的渠道请求身份调用渠道。
     *
     * @param commandDTO      创建交易命令
     * @param transactionType 首次交易类型
     * @return 本地准备结果
     */
    PaymentInitialPreparationResultDTO prepareInitialTransaction(PaymentCreateCommandDTO commandDTO, String transactionType);
}
