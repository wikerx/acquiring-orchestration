package com.scott.payment.merchant.service;

import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.AnalyticsQuery;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.FailureResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.OverviewResponse;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTransactionAnalyticsQueryService
 * @date : 2026-08-07 10:00
 * @email : scott_x@163.com
 * @description : 商户端交易分析只读服务契约，所有查询必须绑定认证商户号并限制返回字段为商户可见聚合信息。
 * @status : create
 */
public interface MerchantTransactionAnalyticsQueryService {

    /**
     * 查询当前商户交易总览。
     *
     * @param merchantId 认证上下文中的商户号
     * @param query 已校验并限制在 31 天内的分析条件
     * @return 当前商户交易总览
     */
    OverviewResponse overview(String merchantId, AnalyticsQuery query);

    /**
     * 查询当前商户失败分析。
     *
     * @param merchantId 认证上下文中的商户号
     * @param query 已校验并限制在 31 天内的分析条件
     * @return 仅包含商户可见失败原因的分析结果
     */
    FailureResponse failures(String merchantId, AnalyticsQuery query);
}
