package com.scott.payment.admin.api.transaction;

import com.scott.payment.admin.application.transaction.AdminTransactionAnalyticsApplicationService;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.AnalyticsQuery;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.MerchantPerformanceResponse;
import com.scott.payment.admin.dto.transaction.AdminTransactionAnalyticsDTOs.OverviewResponse;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AdminTransactionAnalyticsController
 * @date : 2026-08-07 10:00
 * @email : scott_x@163.com
 * @description : 管理端交易分析接口，负责权限校验、请求接收和统一响应，不在接口层执行统计或金额计算。
 * @status : create
 */
@RestController
@RequestMapping("/admin/transactions/analytics")
public class AdminTransactionAnalyticsController {

    /** 管理端交易分析应用服务，不允许为空。 */
    private final AdminTransactionAnalyticsApplicationService analyticsApplicationService;

    /**
     * 创建管理端交易分析接口。
     *
     * @param analyticsApplicationService 交易分析应用服务
     */
    public AdminTransactionAnalyticsController(AdminTransactionAnalyticsApplicationService analyticsApplicationService) {
        this.analyticsApplicationService = analyticsApplicationService;
    }

    /**
     * 查询交易总览。
     *
     * @param query 时间、商户、交易类型、币种和支付维度筛选条件
     * @return 总览指标和图表序列
     */
    @PostMapping("/overview")
    @RequiresPermission("transaction:analytics:view")
    @OperationLog(moduleName = "交易分析", businessType = OperationTypeConstants.QUERY, operation = "查询交易总览")
    public CommonResult<OverviewResponse> overview(@RequestBody(required = false) AnalyticsQuery query) {
        return success(analyticsApplicationService.overview(query));
    }

    /**
     * 查询商户交易表现。
     *
     * @param query 时间、商户、交易类型、币种和支付维度筛选条件
     * @return 商户交易表现列表
     */
    @PostMapping("/merchants")
    @RequiresPermission("transaction:analytics:view")
    @OperationLog(moduleName = "交易分析", businessType = OperationTypeConstants.QUERY, operation = "查询商户表现")
    public CommonResult<MerchantPerformanceResponse> merchants(@RequestBody(required = false) AnalyticsQuery query) {
        return success(analyticsApplicationService.merchantPerformance(query));
    }
}
