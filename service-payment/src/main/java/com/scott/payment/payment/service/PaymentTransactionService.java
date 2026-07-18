package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentQueryResultDTO;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionService
 * @date : 2026-05-31 21:02
 * @email : scott_x@163.com
 * @description : 收单支付交易服务，位于 service-payment 服务层，负责交易动作幂等、状态边界、风控、路由和渠道调用。
 * @status : create
 */
public interface PaymentTransactionService {

    /**
     * 创建一步支付交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    PaymentCreateResultDTO createPayment(PaymentCreateCommandDTO commandDTO);

    /**
     * 创建收单授权交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    PaymentCreateResultDTO createAuthorization(PaymentCreateCommandDTO commandDTO);

    /**
     * 创建预授权交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    PaymentCreateResultDTO createPreAuthorization(PaymentCreateCommandDTO commandDTO);

    /**
     * 创建增量授权交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    PaymentCreateResultDTO createIncrementalAuthorization(PaymentCreateCommandDTO commandDTO);

    /**
     * 发起请款交易。
     *
     * @param commandDTO 请款命令
     * @return 请款结果
     */
    PaymentCreateResultDTO capture(PaymentCreateCommandDTO commandDTO);

    /**
     * 发起退款交易。
     *
     * @param commandDTO 退款命令
     * @return 退款结果
     */
    PaymentCreateResultDTO refund(PaymentCreateCommandDTO commandDTO);

    /**
     * 发起撤销交易。
     *
     * @param commandDTO 撤销命令
     * @return 撤销结果
     */
    PaymentCreateResultDTO voidPayment(PaymentCreateCommandDTO commandDTO);

    /**
     * 查询交易状态。
     *
     * @param commandDTO 查询命令
     * @return 查询结果
     */
    PaymentQueryResultDTO query(PaymentCreateCommandDTO commandDTO);
}
