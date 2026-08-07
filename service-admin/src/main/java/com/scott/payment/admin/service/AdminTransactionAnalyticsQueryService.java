package com.scott.payment.admin.service;

import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.AnalyticsQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.MerchantPerformanceResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.OverviewResponse;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTransactionAnalyticsQueryService
 * @date : 2026-08-07 10:00
 * @email : scott_x@163.com
 * @description : 管理端交易分析只读服务契约，负责按统一首笔交易口径查询总览和商户表现，不处理 HTTP 或权限校验。
 * @status : create
 */
public interface AdminTransactionAnalyticsQueryService {

    /**
     * 查询管理端交易总览。
     *
     * @param query 已校验并限制在 31 天内的分析条件
     * @return 交易总览指标和图表序列
     */
    OverviewResponse overview(AnalyticsQuery query);

    /**
     * 查询管理端商户表现。
     *
     * @param query 已校验并限制在 31 天内的分析条件
     * @return 按交易笔数排序的商户表现
     */
    MerchantPerformanceResponse merchantPerformance(AnalyticsQuery query);
}
