package com.scott.payment.payment.application;

import com.scott.payment.payment.api.internal.dto.PaymentCreateCommandDTO;
import com.scott.payment.payment.api.internal.dto.PaymentCreateResultDTO;
import com.scott.payment.payment.api.internal.dto.PaymentQueryResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelCallbackResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchCommandDTO;
import com.scott.payment.payment.api.internal.dto.TransactionChannelMatchResultDTO;
import com.scott.payment.payment.api.internal.dto.TransactionMerchantApiResponseLogUpdateCommandDTO;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.payment.service.TransactionCallbackService;
import com.scott.payment.payment.service.TransactionChannelMatchService;
import com.scott.payment.payment.service.TransactionRecordService;
import com.scott.payment.payment.service.TransactionQueryService;
import com.scott.payment.payment.service.PaymentTransactionService;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.ChannelCallbackQuery;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.ChannelLogQuery;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.MerchantNotificationQuery;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionDetailResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOperationSearchResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOperationResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOrderResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionPageQuery;
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
     * 交易聚合查询服务。
     */
    private final TransactionQueryService transactionQueryService;

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
     * @param transactionQueryService 交易聚合查询服务
     * @param transactionRecordService 交易事实记录服务
     */
    public PaymentTransactionApplicationService(PaymentTransactionService paymentTransactionService,
                                                TransactionCallbackService transactionCallbackService,
                                                TransactionChannelMatchService transactionChannelMatchService,
                                                TransactionQueryService transactionQueryService,
                                                TransactionRecordService transactionRecordService) {
        this.paymentTransactionService = paymentTransactionService;
        this.transactionCallbackService = transactionCallbackService;
        this.transactionChannelMatchService = transactionChannelMatchService;
        this.transactionQueryService = transactionQueryService;
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
     * 分页查询交易主单。
     *
     * @param query 查询条件
     * @return 主单分页结果
     */
    public PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query) {
        return transactionQueryService.pageOrders(query);
    }

    /**
     * 分页查询交易动作单。
     *
     * @param query 查询条件
     * @return 动作单分页结果
     */
    public PageResult<TransactionOperationResponse> pageOperations(TransactionPageQuery query) {
        return transactionQueryService.pageOperations(query);
    }

    /**
     * 分页查询交易动作单，并返回当前查询条件下的全量统计。
     *
     * @param query 查询条件
     * @return 交易动作分页与统计响应
     */
    public TransactionOperationSearchResponse searchOperations(TransactionPageQuery query) {
        return transactionQueryService.searchOperations(query);
    }

    /**
     * 查询交易详情聚合数据。
     *
     * @param transactionId 平台交易 ID
     * @return 交易详情
     */
    public TransactionDetailResponse detail(String transactionId) {
        return transactionQueryService.detail(transactionId);
    }

    /**
     * 分页查询渠道交互日志。
     *
     * @param query 查询条件
     * @return 渠道交互日志分页结果
     */
    public PageResult<?> pageChannelLogs(ChannelLogQuery query) {
        return transactionQueryService.pageChannelLogs(query);
    }

    /**
     * 分页查询渠道回调业务记录。
     *
     * @param query 查询条件
     * @return 渠道回调业务记录分页结果
     */
    public PageResult<?> pageChannelCallbacks(ChannelCallbackQuery query) {
        return transactionQueryService.pageChannelCallbacks(query);
    }

    /**
     * 分页查询商户通知任务。
     *
     * @param query 查询条件
     * @return 商户通知任务分页结果
     */
    public PageResult<?> pageMerchantNotifications(MerchantNotificationQuery query) {
        return transactionQueryService.pageMerchantNotifications(query);
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
