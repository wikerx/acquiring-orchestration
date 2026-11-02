package com.scott.payment.admin.api.exchange;

import com.scott.payment.admin.application.exchange.AdminExchangeRateVolatilityApplicationService;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.TrendSeries;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.VolatilityOverview;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.VolatilityQuery;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/** 管理端汇率波动只读查询接口。 */
@RestController
@RequestMapping("/admin/exchange/volatility")
public class AdminExchangeRateVolatilityController {

    private final AdminExchangeRateVolatilityApplicationService applicationService;

    /** 创建交易汇率与结算汇率严格分离的波动查询接口。 */
    public AdminExchangeRateVolatilityController(AdminExchangeRateVolatilityApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 查询币种对波动摘要。 */
    @PostMapping("/overview")
    @RequiresPermission("exchange:volatility:list")
    public CommonResult<List<VolatilityOverview>> overview(@Valid @RequestBody VolatilityQuery request) {
        return success(applicationService.overview(request));
    }

    /** 查询跨来源趋势点及其来源证据。 */
    @PostMapping("/trend")
    @RequiresPermission("exchange:volatility:query")
    public CommonResult<List<TrendSeries>> trend(@Valid @RequestBody VolatilityQuery request) {
        return success(applicationService.trend(request));
    }

    /** 导出当前筛选的汇率波动摘要。 */
    /** 按当前查询口径导出波动摘要，导出数据不参与阈值判断。 */
    @PostMapping("/export")
    @RequiresPermission("exchange:volatility:export")
    @OperationLog(moduleName = "汇率波动", businessType = OperationTypeConstants.EXPORT, operation = "导出汇率波动")
    public void export(@Valid @RequestBody VolatilityQuery request, HttpServletResponse response) {
        applicationService.exportVolatility(request, currentOperator(), response);
    }

    /** 从已认证上下文取得导出审计操作人。 */
    private String currentOperator() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        return account == null || account.getLoginAccount() == null ? "admin" : account.getLoginAccount();
    }
}
