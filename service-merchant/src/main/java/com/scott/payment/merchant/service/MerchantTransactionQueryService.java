package com.scott.payment.merchant.service;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOperationSearchResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionOrderResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionDTOs.TransactionPageQuery;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTransactionQueryService
 * @date : 2026-07-20 00:00
 * @email : scott_x@163.com
 * @description : 商户后台交易只读查询服务，位于 service-merchant 服务层，按当前商户边界直接查询交易查询库/备库，不承担退款等资金状态变更。
 * @status : create
 */
public interface MerchantTransactionQueryService {

    /**
     * 分页查询当前商户交易生命周期主单。
     *
     * @param query 已注入当前登录商户号的查询条件
     * @return 主单分页结果
     */
    PageResult<TransactionOrderResponse> pageOrders(TransactionPageQuery query);

    /**
     * 分页查询当前商户交易动作单并聚合统计。
     *
     * @param query 已注入当前登录商户号的查询条件
     * @return 动作单分页与统计
     */
    TransactionOperationSearchResponse searchOperations(TransactionPageQuery query);

    /**
     * 查询当前商户交易聚合详情。
     *
     * @param merchantId 当前登录商户号
     * @param transactionId 平台交易 ID
     * @param transactionDateTime 列表返回的真实交易分片时间
     * @return 商户可见交易详情
     */
    TransactionDetailResponse detail(String merchantId,
                                     String transactionId,
                                     LocalDateTime transactionDateTime,
                                     LocalDateTime rootTransactionDateTime);
}
