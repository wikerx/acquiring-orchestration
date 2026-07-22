package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelCallbackQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.ChannelLogQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.MerchantNotificationQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionDTOs.TransactionPageQuery;
import com.scott.payment.component.core.model.PageResult;

import java.util.Map;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTransactionQueryService
 * @date : 2026-07-21 00:00
 * @email : scott_x@163.com
 * @description : 管理后台交易只读查询服务，位于 service-admin 服务层，直接读取交易备库分表。
 * @status : create
 */
public interface AdminTransactionQueryService {

    PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query);

    PageResult<TransactionOperationResponse> pageOperations(TransactionPageQuery query);

    TransactionOperationSearchResponse searchOperations(TransactionPageQuery query);

    TransactionDetailResponse detail(String transactionId);

    PageResult<Map<String, Object>> pageChannelLogs(ChannelLogQuery query);

    PageResult<Map<String, Object>> pageChannelCallbacks(ChannelCallbackQuery query);

    PageResult<Map<String, Object>> pageMerchantNotifications(MerchantNotificationQuery query);
}
