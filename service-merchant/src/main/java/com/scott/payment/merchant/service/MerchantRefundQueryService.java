package com.scott.payment.merchant.service;

import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundDetailResponse;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundQuery;
import com.scott.payment.merchant.dto.transaction.MerchantRefundDTOs.RefundSearchResponse;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantRefundQueryService
 * @date : 2026-08-08 00:10
 * @email : scott_x@163.com
 * @description : 商户端退款只读查询服务，在 service-merchant 内按认证商户边界读取退款分页、统计和详情，不执行资金状态变更。
 * @status : create
 */
public interface MerchantRefundQueryService {

    /**
     * 查询当前认证商户的退款与撤销分页和统计。
     *
     * @param query 已由应用层覆盖认证商户号的查询条件
     * @return 商户可见退款分页和统计
     */
    RefundSearchResponse search(RefundQuery query);

    /**
     * 查询当前认证商户的单笔退款详情。
     *
     * @param merchantId 当前认证商户号
     * @param transactionId 退款或撤销交易号
     * @param transactionDateTime 列表返回的真实交易分片时间
     * @return 商户可见退款详情
     */
    RefundDetailResponse detail(String merchantId,
                                String transactionId,
                                LocalDateTime transactionDateTime);
}
