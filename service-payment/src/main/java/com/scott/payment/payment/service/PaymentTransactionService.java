package com.scott.payment.payment.service;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentQueryResultDTO;
import com.scott.payment.payment.service.dto.PaymentInitialPreparationResultDTO;

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
     * 只准备一步支付的本地交易事实，不调用外部渠道。
     *
     * <p>用于 Hosted Checkout 等需要先完成 3DS 再提交资金请求的编排。返回后主单、动作单、
     * 幂等记录和 INIT 渠道请求均已提交，因此后续 3DS 失败也必须收敛同一笔交易。</p>
     *
     * @param commandDTO 创建交易命令
     * @return 已提交的本地准备结果
     */
    PaymentInitialPreparationResultDTO preparePayment(PaymentCreateCommandDTO commandDTO);

    /**
     * 只准备授权交易的本地交易事实，不调用外部渠道。
     *
     * @param commandDTO 创建交易命令
     * @return 已提交的本地准备结果
     */
    PaymentInitialPreparationResultDTO prepareAuthorization(PaymentCreateCommandDTO commandDTO);

    /**
     * 提交已经准备并持久化的首次资金请求。
     *
     * <p>提交前会通过数据库 CAS 抢占 INIT 渠道请求；重复调用不会重复请求 PSP。</p>
     *
     * @param preparationResultDTO 本地准备结果
     * @return 当前交易结果
     */
    PaymentCreateResultDTO submitPreparedTransaction(PaymentInitialPreparationResultDTO preparationResultDTO);

    /**
     * 从已持久化交易事实恢复并提交首次资金请求，供 3DS 浏览器回跳使用。
     *
     * @param commandDTO 带服务端确认 3DS 结果的交易命令
     * @return 当前交易结果
     */
    PaymentCreateResultDTO resumePreparedTransaction(PaymentCreateCommandDTO commandDTO);

    /**
     * 将尚未提交资金渠道的已准备交易收敛为失败终态。
     *
     * @param commandDTO 已准备交易身份
     * @param failureCode 稳定失败原因码
     * @param failureMessage 后台可见的脱敏失败说明
     * @return 失败交易结果
     */
    PaymentCreateResultDTO failPreparedTransaction(PaymentCreateCommandDTO commandDTO,
                                                   String failureCode,
                                                   String failureMessage);

    /**
     * 标记本笔交易实际启用了 3DS。认证成功后 indicator 可由 ECI 覆盖，认证失败仍保留 REQUIRED。
     *
     * @param transactionId 平台交易号
     * @param transactionDateTime 交易分片时间
     * @param indicator REQUIRED 或 ECI
     */
    void markThreeDsIndicator(String transactionId,
                              java.time.LocalDateTime transactionDateTime,
                              String indicator);

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
     * 发起预授权完成交易。
     *
     * @param commandDTO 预授权完成命令
     * @return 预授权完成结果
     */
    PaymentCreateResultDTO preAuthCompletion(PaymentCreateCommandDTO commandDTO);

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
