package com.scott.payment.admin.api.exchange;

import com.scott.payment.admin.application.exchange.AdminExchangeRateVolatilityApplicationService;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventActionRequest;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventQuery;
import com.scott.payment.admin.dto.exchange.ExchangeRateVolatilityDTOs.AlertEventResponse;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/** 管理端汇率波动预警事件接口；事件事实不提供修改、删除或导入能力。 */
@RestController
@RequestMapping("/admin/exchange/alert-events")
public class AdminExchangeRateAlertEventController {

    private final AdminExchangeRateVolatilityApplicationService applicationService;

    /** 创建只暴露事件查询和受控状态操作的管理端接口。 */
    public AdminExchangeRateAlertEventController(AdminExchangeRateVolatilityApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 按管理端筛选条件分页查询不可变预警事件事实。 */
    @PostMapping("/search")
    @RequiresPermission("exchange:alert-event:list")
    public CommonResult<PageResult<AlertEventResponse>> page(@RequestBody(required = false) @Valid AlertEventQuery request) {
        return success(applicationService.pageEvents(request));
    }

    /** 查询单个事件冻结的规则、窗口证据和审计信息。 */
    @GetMapping("/{id}")
    @RequiresPermission("exchange:alert-event:query")
    public CommonResult<AlertEventResponse> detail(@PathVariable("id") Long id) {
        return success(applicationService.getEvent(id));
    }

    /** 使用前端读取版本确认 OPEN 事件，避免覆盖并发状态变化。 */
    @PutMapping("/{id}/acknowledge")
    @RequiresPermission("exchange:alert-event:ack")
    @OperationLog(moduleName = "汇率预警事件", businessType = OperationTypeConstants.AUDIT, operation = "确认汇率预警事件")
    public CommonResult<AlertEventResponse> acknowledge(@PathVariable("id") Long id,
                                                        @Valid @RequestBody AlertEventActionRequest request) {
        return success(applicationService.acknowledgeEvent(id, request));
    }

    /** 使用前端读取版本关闭已自动恢复事件，持续告警不允许人工掩盖。 */
    @PutMapping("/{id}/close")
    @RequiresPermission("exchange:alert-event:close")
    @OperationLog(moduleName = "汇率预警事件", businessType = OperationTypeConstants.UPDATE, operation = "关闭汇率预警事件")
    public CommonResult<AlertEventResponse> close(@PathVariable("id") Long id,
                                                  @Valid @RequestBody AlertEventActionRequest request) {
        return success(applicationService.closeEvent(id, request));
    }

    /** 按当前筛选条件导出具有权限的预警事件事实。 */
    @PostMapping("/export")
    @RequiresPermission("exchange:alert-event:export")
    @OperationLog(moduleName = "汇率预警事件", businessType = OperationTypeConstants.EXPORT, operation = "导出汇率预警事件")
    public void export(@RequestBody(required = false) AlertEventQuery request, HttpServletResponse response) {
        applicationService.exportEvents(request, currentOperator(), response);
    }

    /** 从已认证上下文取得导出审计操作人。 */
    private String currentOperator() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        return account == null || account.getLoginAccount() == null ? "admin" : account.getLoginAccount();
    }
}
