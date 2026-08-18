package com.scott.payment.merchant.api.transaction;

import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import com.scott.payment.merchant.application.transaction.MerchantTransactionAnalyticsApplicationService;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.AnalyticsQuery;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.FailureResponse;
import com.scott.payment.merchant.dto.transaction.MerchantTransactionAnalyticsDTOs.OverviewResponse;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantTransactionAnalyticsController
 * @date : 2026-08-07 10:00
 * @email : scott_x@163.com
 * @description : 商户端交易分析接口，从认证上下文绑定商户号并返回商户可见聚合数据，不接受前端指定数据归属。
 * @status : create
 */
@RestController
@RequestMapping("/merchant/transactions/analytics")
public class MerchantTransactionAnalyticsController {

    /** 商户端交易分析应用服务，不允许为空。 */
    private final MerchantTransactionAnalyticsApplicationService analyticsApplicationService;

    /**
     * 创建商户端交易分析接口。
     *
     * @param analyticsApplicationService 商户交易分析应用服务
     */
    public MerchantTransactionAnalyticsController(MerchantTransactionAnalyticsApplicationService analyticsApplicationService) {
        this.analyticsApplicationService = analyticsApplicationService;
    }

    /**
     * 查询当前商户交易总览。
     *
     * @param query 时间、交易类型、币种和支付维度筛选条件
     * @return 当前商户总览指标和图表序列
     */
    @PostMapping("/overview")
    @RequiresPermission("merchant:transaction:analytics:view")
    @OperationLog(moduleName = "商户交易分析", businessType = OperationTypeConstants.QUERY, operation = "查询商户交易总览")
    public CommonResult<OverviewResponse> overview(@RequestBody(required = false) AnalyticsQuery query) {
        return success(analyticsApplicationService.overview(currentMerchantId(), query));
    }

    /**
     * 查询当前商户失败分析。
     *
     * @param query 时间、交易类型、币种和支付维度筛选条件
     * @return 商户可见失败原因和趋势
     */
    @PostMapping("/failures")
    @RequiresPermission("merchant:transaction:analytics:view")
    @OperationLog(moduleName = "商户交易分析", businessType = OperationTypeConstants.QUERY, operation = "查询商户交易失败分析")
    public CommonResult<FailureResponse> failures(@RequestBody(required = false) AnalyticsQuery query) {
        return success(analyticsApplicationService.failures(currentMerchantId(), query));
    }

    /**
     * 从认证上下文读取商户号，作为所有统计 SQL 的强制数据边界。
     *
     * @return 已认证商户号
     * @throws ServiceException 认证上下文缺失或未绑定商户时抛出
     */
    private String currentMerchantId() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null || !StringUtils.hasText(account.getMerchantId())) {
            throw new ServiceException(ApiResultEnum.UNAUTHORIZED.getCode(), "merchant context missing");
        }
        return account.getMerchantId();
    }
}
