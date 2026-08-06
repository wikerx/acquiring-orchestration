package com.scott.payment.payment.service;

import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundDetailResponse;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundQuery;
import com.scott.payment.payment.service.dto.refund.RefundManagementDTOs.RefundSearchResponse;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RefundManagementQueryService
 * @date : 2026-08-06 15:50
 * @email : scott_x@163.com
 * @description : 退款管理查询服务，统一 Admin 与 Merchant 的退款/撤销筛选、统计和精确分片详情读取。
 * @status : create
 */
public interface RefundManagementQueryService {

    /** @param query 查询条件 @return 退款分页和统计 */
    RefundSearchResponse search(RefundQuery query);

    /**
     * 查询单笔退款详情。
     *
     * @param transactionId 退款交易号
     * @param transactionDateTime 退款动作真实分片时间
     * @param merchantId 商户数据边界；Admin 可为空
     * @return 退款详情
     */
    RefundDetailResponse detail(String transactionId,
                                LocalDateTime transactionDateTime,
                                String merchantId);
}
