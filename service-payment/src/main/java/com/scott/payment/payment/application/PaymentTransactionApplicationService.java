package com.scott.payment.payment.application;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentQueryResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionMerchantApiResponseLogUpdateCommandDTO;
import com.scott.payment.payment.service.TransactionCallbackService;
import com.scott.payment.payment.service.TransactionChannelMatchService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.PaymentTransactionService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTransactionApplicationService
 * @date : 2026-07-14 12:30
 * @email : scott_x@163.com
 * @description : 收单交易应用服务，位于 service-payment 应用编排层，负责承接内部交易动作命令并委托交易服务执行幂等、风控、路由和渠道调用。
 * @status : create
 */
@Service
public class PaymentTransactionApplicationService {

    /**
     * 收单支付交易服务。
     */
    private final PaymentTransactionService paymentTransactionService;

    /**
     * 交易渠道回调服务。
     */
    private final TransactionCallbackService transactionCallbackService;

    /**
     * 渠道交易查询勾兑服务。
     */
    private final TransactionChannelMatchService transactionChannelMatchService;

    /**
     * 交易事实记录服务，用于回写 OpenAPI 响应加密摘要等审计信息。
     */
    private final TransactionRecordService transactionRecordService;

    /**
     * 创建收单交易应用服务。
     *
     * @param paymentTransactionService 收单支付交易服务
     * @param transactionCallbackService 交易渠道回调服务
     * @param transactionChannelMatchService 渠道交易查询勾兑服务
     * @param transactionRecordService 交易事实记录服务
     */
    public PaymentTransactionApplicationService(PaymentTransactionService paymentTransactionService,
                                                TransactionCallbackService transactionCallbackService,
                                                TransactionChannelMatchService transactionChannelMatchService,
                                                TransactionRecordService transactionRecordService) {
        this.paymentTransactionService = paymentTransactionService;
        this.transactionCallbackService = transactionCallbackService;
        this.transactionChannelMatchService = transactionChannelMatchService;
        this.transactionRecordService = transactionRecordService;
    }

    /**
     * 创建一步支付交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    public PaymentCreateResultDTO createPayment(PaymentCreateCommandDTO commandDTO) {
        return paymentTransactionService.createPayment(commandDTO);
    }

    /**
     * 创建收单授权交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    public PaymentCreateResultDTO createAuthorization(PaymentCreateCommandDTO commandDTO) {
        return paymentTransactionService.createAuthorization(commandDTO);
    }

    /**
     * 创建预授权交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    public PaymentCreateResultDTO createPreAuthorization(PaymentCreateCommandDTO commandDTO) {
        return paymentTransactionService.createPreAuthorization(commandDTO);
    }

    /**
     * 创建增量授权交易。
     *
     * @param commandDTO 创建交易命令
     * @return 创建交易结果
     */
    public PaymentCreateResultDTO createIncrementalAuthorization(PaymentCreateCommandDTO commandDTO) {
        return paymentTransactionService.createIncrementalAuthorization(commandDTO);
    }

    /**
     * 发起请款交易。
     *
     * @param commandDTO 请款命令
     * @return 请款结果
     */
    public PaymentCreateResultDTO capture(PaymentCreateCommandDTO commandDTO) {
        return paymentTransactionService.capture(commandDTO);
    }

    /**
     * 发起预授权完成交易。
     *
     * @param commandDTO 预授权完成命令
     * @return 预授权完成结果
     */
    public PaymentCreateResultDTO preAuthCompletion(PaymentCreateCommandDTO commandDTO) {
        return paymentTransactionService.preAuthCompletion(commandDTO);
    }

    /**
     * 发起退款交易。
     *
     * @param commandDTO 退款命令
     * @return 退款结果
     */
    public PaymentCreateResultDTO refund(PaymentCreateCommandDTO commandDTO) {
        return paymentTransactionService.refund(commandDTO);
    }

    /**
     * 发起撤销交易。
     *
     * @param commandDTO 撤销命令
     * @return 撤销结果
     */
    public PaymentCreateResultDTO voidPayment(PaymentCreateCommandDTO commandDTO) {
        return paymentTransactionService.voidPayment(commandDTO);
    }

    /**
     * 查询交易状态。
     *
     * @param commandDTO 查询命令
     * @return 查询结果
     */
    public PaymentQueryResultDTO query(PaymentCreateCommandDTO commandDTO) {
        return paymentTransactionService.query(commandDTO);
    }

    /**
     * 记录渠道回调。
     *
     * @param commandDTO 渠道回调内部命令
     * @return 渠道回调记录结果
     */
    public TransactionChannelCallbackResultDTO recordChannelCallback(TransactionChannelCallbackCommandDTO commandDTO) {
        return transactionCallbackService.recordChannelCallback(commandDTO);
    }

    /**
     * 执行渠道交易查询勾兑。
     *
     * @param commandDTO 渠道查询勾兑命令
     * @return 本次处理结果
     */
    public TransactionChannelMatchResultDTO matchDueChannelTransactions(TransactionChannelMatchCommandDTO commandDTO) {
        return transactionChannelMatchService.matchDue(commandDTO);
    }

    /**
     * 回写商户 OpenAPI 响应密文摘要。
     *
     * @param commandDTO 响应日志回写命令
     * @return true 表示命中并更新日志
     */
    public boolean updateMerchantApiResponseLog(TransactionMerchantApiResponseLogUpdateCommandDTO commandDTO) {
        return transactionRecordService.updateMerchantApiResponseLog(commandDTO);
    }
}
