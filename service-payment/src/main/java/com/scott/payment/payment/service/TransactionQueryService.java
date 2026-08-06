package com.scott.payment.payment.service;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.ChannelCallbackQuery;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.ChannelLogQuery;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.MerchantNotificationQuery;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionDetailResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOperationSearchResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOperationResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionOrderResponse;
import com.scott.payment.payment.service.dto.transaction.TransactionQueryDTOs.TransactionPageQuery;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : TransactionQueryService
 * @date : 2026-07-14 23:16
 * @email : scott_x@163.com
 * @description : 交易聚合查询服务，位于 service-payment 服务层，统一按 transaction_date_time 分表规则查询交易主单、动作单、渠道日志和商户通知。
 * @status : create
 */
public interface TransactionQueryService {

    /**
     * 分页查询交易生命周期主单。
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
     * 分页查询交易动作单，并按相同查询条件返回全量统计。
     *
     * @param query 查询条件
     * @return 动作单分页与查询条件统计结果
     */
    TransactionOperationSearchResponse searchOperations(TransactionPageQuery query);

    /**
     * 查询交易详情聚合数据。
     *
     * @param transactionId 平台交易 ID
     * @param transactionDateTime 列表返回的当前动作真实分片时间
     * @param rootTransactionDateTime 列表返回的生命周期根主单真实分片时间
     * @return 交易详情
     */
    TransactionDetailResponse detail(String transactionId,
                                     java.time.LocalDateTime transactionDateTime,
                                     java.time.LocalDateTime rootTransactionDateTime);

    /**
     * 分页查询渠道交互日志。
     *
     * @param query 查询条件
     * @return 渠道交互日志分页结果
     */
    PageResult<?> pageChannelLogs(ChannelLogQuery query);

    /**
     * 分页查询渠道回调业务记录。
     *
     * @param query 查询条件
     * @return 渠道回调业务记录分页结果
     */
    PageResult<?> pageChannelCallbacks(ChannelCallbackQuery query);

    /**
     * 分页查询商户通知任务。
     *
     * @param query 查询条件
     * @return 商户通知任务分页结果
     */
    PageResult<?> pageMerchantNotifications(MerchantNotificationQuery query);
}
