package com.scott.payment.admin.application.transaction;

import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.AnalyticsQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ChannelBreakdown;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ChannelDiagnosis;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ChannelDiagnosisQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ChannelFailedTransaction;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ChannelPerformanceResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.FailureResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.MerchantPerformanceResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.OverviewResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.ThreeDsResponse;
import com.scott.payment.admin.service.AdminTransactionAnalyticsQueryService;
import com.scott.payment.admin.service.AdminMerchantDataScope;
import com.scott.payment.admin.service.AdminMerchantDataScopeResolver;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.PageResult;
import org.springframework.stereotype.Service;

/**
 * 管理端交易分析应用服务，编排分析用例并将接口层与只读统计查询实现隔离。
 */
@Service
public class AdminTransactionAnalyticsApplicationService {

    /**
     * 管理端交易分析只读查询依赖，不允许为空。
     */
    private final AdminTransactionAnalyticsQueryService analyticsQueryService;
    private final AdminMerchantDataScopeResolver merchantDataScopeResolver;

    /**
     * 创建管理端交易分析应用服务。
     *
     * @param analyticsQueryService 交易分析只读查询服务
     * @param merchantDataScopeResolver 当前账号商户范围解析器
     */
    public AdminTransactionAnalyticsApplicationService(AdminTransactionAnalyticsQueryService analyticsQueryService,
                                                        AdminMerchantDataScopeResolver merchantDataScopeResolver) {
        this.analyticsQueryService = analyticsQueryService;
        this.merchantDataScopeResolver = merchantDataScopeResolver;
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
        return analyticsQueryService.channelPerformance(query, channelScope(InternalAuthContextHolder.get()));
    }

    /** 查询当前账号可见的渠道及支付方式组合。 */
    public ChannelBreakdown channelBreakdown(AnalyticsQuery query) {
        return analyticsQueryService.channelBreakdown(query, channelScope(InternalAuthContextHolder.get()));
    }

    /** 查询当前账号可见的渠道组合诊断。 */
    public ChannelDiagnosis channelDiagnosis(ChannelDiagnosisQuery query) {
        return analyticsQueryService.channelDiagnosis(query, channelScope(InternalAuthContextHolder.get()));
    }

    /** 查询当前账号可见的组合内失败交易。 */
    public PageResult<ChannelFailedTransaction> channelFailedTransactions(ChannelDiagnosisQuery query) {
        var account = InternalAuthContextHolder.get();
        var scope = channelScope(account);
        requirePermission(account, "transaction:operation:list");
        return analyticsQueryService.channelFailedTransactions(query, scope);
    }

    private AdminMerchantDataScope channelScope(InternalAuthAccount account) {
        if (account == null) throw new ServiceException(ApiResultEnum.UNAUTHORIZED);
        requirePermission(account, "transaction:analytics:view");
        return merchantDataScopeResolver.resolve(account);
    }

    private void requirePermission(InternalAuthAccount account, String permission) {
        var permissions = account.getPermissions();
        if (permissions == null || !(permissions.contains(permission) || permissions.contains("*:*:*"))) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
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
