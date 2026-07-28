package com.scott.payment.admin.client.payment;

import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelCallbackQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelLogQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionActionResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.admin.client.payment.dto.PaymentTransactionActionClientRequestDTO;
import com.scott.payment.component.core.model.PageResult;

import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentInternalClient
 * @date : 2026-07-14 23:57
 * @email : scott_x@163.com
 * @description : service-payment 内部查询客户端契约，位于 service-admin 客户端层，为交易管理页面封装只读内部接口调用。
 * @status : create
 */
public interface PaymentInternalClient {

    /**
     * 分页查询交易主单。
     *
     * @param query 查询条件
     * @return 主单分页结果
     */
    PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query);

    /**
     * 分页查询交易动作单。
     *
     * @param query 查询条件
     * @return 动作单分页结果
     */
    PageResult<TransactionOperationResponse> pageOperations(TransactionPageQuery query);

    /**
     * 分页查询交易动作单，并返回当前查询条件下的全量统计。
     *
     * @param query 查询条件
     * @return 动作单分页与统计结果
     */
    TransactionOperationSearchResponse searchOperations(TransactionPageQuery query);

    /**
     * 通过支付核心发起请款动作。
     *
     * @param requestDTO 支付核心内部请款命令
     * @return 请款动作结果
     */
    TransactionActionResponse capture(PaymentTransactionActionClientRequestDTO requestDTO);

    /**
     * 通过支付核心发起退款动作。
     *
     * @param commandDTO 支付核心内部退款命令
     * @return 退款动作结果
     */
    TransactionActionResponse refund(PaymentTransactionActionClientRequestDTO requestDTO);

    /**
     * 通过支付核心发起撤销动作。
     *
     * @param commandDTO 支付核心内部撤销命令
     * @return 撤销动作结果
     */
    TransactionActionResponse voidPayment(PaymentTransactionActionClientRequestDTO requestDTO);

    /**
     * 查询交易聚合详情。
     *
     * @param transactionId 平台交易 ID
     * @return 交易聚合详情
     */
    TransactionDetailResponse detail(String transactionId);

    /**
     * 分页查询渠道交互日志。
     *
     * @param query 查询条件
     * @return 渠道交互日志分页结果
     */
    PageResult<Map<String, Object>> pageChannelLogs(ChannelLogQuery query);

    /**
     * 分页查询渠道回调业务记录。
     *
     * @param query 查询条件
     * @return 渠道回调分页结果
     */
    PageResult<Map<String, Object>> pageChannelCallbacks(ChannelCallbackQuery query);

    /**
     * 分页查询商户通知任务。
     *
     * @param query 查询条件
     * @return 商户通知任务分页结果
     */
    PageResult<Map<String, Object>> pageMerchantNotifications(MerchantNotificationQuery query);
}
