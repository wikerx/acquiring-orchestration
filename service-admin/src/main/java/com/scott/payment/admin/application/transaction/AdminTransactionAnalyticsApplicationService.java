package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.AnalyticsQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ChannelPerformanceResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.FailureResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.MerchantPerformanceResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.OverviewResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ThreeDsResponse;
import com.scott.payment.admin.service.AdminTransactionAnalyticsQueryService;
import org.springframework.stereotype.Service;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTransactionAnalyticsApplicationService
 * @date : 2026-08-07 10:00
 * @email : scott_x@163.com
 * @description : 管理端交易分析应用服务，编排分析用例并将接口层与只读统计查询实现隔离。
 * @status : create
 */
@Service
public class AdminTransactionAnalyticsApplicationService {

    /** 管理端交易分析只读查询依赖，不允许为空。 */
    private final AdminTransactionAnalyticsQueryService analyticsQueryService;

    /**
     * 创建管理端交易分析应用服务。
     *
     * @param analyticsQueryService 交易分析只读查询服务
     */
    public AdminTransactionAnalyticsApplicationService(AdminTransactionAnalyticsQueryService analyticsQueryService) {
        this.analyticsQueryService = analyticsQueryService;
    }

    /**
     * 查询交易总览。
     *
     * @param query 页面筛选条件
     * @return 总览指标和图表序列
     */
    public OverviewResponse overview(AnalyticsQuery query) {
        return analyticsQueryService.overview(query);
    }

    /**
     * 查询商户表现。
     *
     * @param query 页面筛选条件
     * @return 商户表现列表
     */
    public MerchantPerformanceResponse merchantPerformance(AnalyticsQuery query) {
        return analyticsQueryService.merchantPerformance(query);
    }

    /**
     * 查询管理端失败分析。
     *
     * @param query 页面筛选条件
     * @return 后台可见失败分析
     */
    public FailureResponse failures(AnalyticsQuery query) {
        return analyticsQueryService.failures(query);
    }

    /**
     * 查询渠道请求及最终交易表现。
     *
     * @param query 页面筛选条件
     * @return 渠道表现分析
     */
    public ChannelPerformanceResponse channelPerformance(AnalyticsQuery query) {
        return analyticsQueryService.channelPerformance(query);
    }

    /**
     * 查询按交易去重的3DS认证分析。
     *
     * @param query 页面筛选条件
     * @return 3DS认证分析
     */
    public ThreeDsResponse threeDs(AnalyticsQuery query) {
        return analyticsQueryService.threeDs(query);
    }
}
