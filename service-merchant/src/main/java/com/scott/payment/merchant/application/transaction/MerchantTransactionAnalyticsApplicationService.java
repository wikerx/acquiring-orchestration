package com.scott.payment.merchant.application.transaction;

import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.AnalyticsQuery;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.FailureResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.OverviewResponse;
import com.scott.payment.merchant.service.MerchantTransactionAnalyticsQueryService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTransactionAnalyticsApplicationService
 * @date : 2026-08-07 10:00
 * @email : scott_x@163.com
 * @description : 商户端交易分析应用服务，将认证商户边界传递给只读统计服务并隔离接口层与数据访问实现。
 * @status : create
 */
@Service
public class MerchantTransactionAnalyticsApplicationService {

    /** 商户端交易分析只读查询依赖，不允许为空。 */
    private final MerchantTransactionAnalyticsQueryService analyticsQueryService;

    /**
     * 创建商户端交易分析应用服务。
     *
     * @param analyticsQueryService 商户交易分析只读查询服务
     */
    public MerchantTransactionAnalyticsApplicationService(MerchantTransactionAnalyticsQueryService analyticsQueryService) {
        this.analyticsQueryService = analyticsQueryService;
    }

    /**
     * 查询当前商户交易总览。
     *
     * @param merchantId 认证上下文中的商户号
     * @param query 页面筛选条件
     * @return 当前商户总览指标和图表序列
     */
    public OverviewResponse overview(String merchantId, AnalyticsQuery query) {
        return analyticsQueryService.overview(merchantId, query);
    }

    /**
     * 查询当前商户失败分析。
     *
     * @param merchantId 认证上下文中的商户号
     * @param query 页面筛选条件
     * @return 商户可见失败分析
     */
    public FailureResponse failures(String merchantId, AnalyticsQuery query) {
        return analyticsQueryService.failures(merchantId, query);
    }
}
