package com.scott.payment.admin.api.export;

import com.scott.payment.admin.application.export.AdminExportTaskApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeBusinessRateSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeAlertEventSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeAlertRuleSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeRawRateSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeRuleSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeSourceSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeUsageSnapshotSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.ExchangeVolatilitySubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理端汇率事实异步导出提交接口，保留既有同步接口用于兼容旧调用方。
 */
@RestController
@RequestMapping("/admin/exchange")
public class AdminExchangeExportTaskController {
    private final AdminExportTaskApplicationService applicationService;

    /**
     * 创建管理端汇率事实异步导出提交接口。
     *
     * @param applicationService 统一异步导出编排服务
     */
    public AdminExchangeExportTaskController(AdminExportTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 冻结原始汇率筛选条件并创建后台任务。 */
    @PostMapping("/raw-rates/export-task")
    @RequiresPermission("exchange:raw-rate:export")
    @OperationLog(moduleName = "原始汇率管理", businessType = OperationTypeConstants.EXPORT,
            operation = "创建原始汇率导出任务")
    public CommonResult<TaskSummary> submitRawRates(
            @Valid @RequestBody ExchangeRawRateSubmitRequest request) {
        return success(applicationService.submitExchangeRawRates(request));
    }

    /** 冻结业务汇率筛选条件并创建后台任务。 */
    @PostMapping("/business-rates/export-task")
    @RequiresPermission("exchange:business-rate:export")
    @OperationLog(moduleName = "业务汇率管理", businessType = OperationTypeConstants.EXPORT,
            operation = "创建业务汇率导出任务")
    public CommonResult<TaskSummary> submitBusinessRates(
            @Valid @RequestBody ExchangeBusinessRateSubmitRequest request) {
        return success(applicationService.submitExchangeBusinessRates(request));
    }

    /** 冻结汇率使用快照筛选条件并创建后台任务。 */
    @PostMapping("/usage-snapshots/export-task")
    @RequiresPermission("exchange:usage-snapshot:export")
    @OperationLog(moduleName = "汇率使用快照", businessType = OperationTypeConstants.EXPORT,
            operation = "创建汇率使用快照导出任务")
    public CommonResult<TaskSummary> submitUsageSnapshots(
            @Valid @RequestBody ExchangeUsageSnapshotSubmitRequest request) {
        return success(applicationService.submitExchangeUsageSnapshots(request));
    }

    /** 冻结汇率源筛选条件并创建后台任务。 */
    @PostMapping("/sources/export-task")
    @RequiresPermission("exchange:source:export")
    @OperationLog(moduleName = "汇率源管理", businessType = OperationTypeConstants.EXPORT,
            operation = "创建汇率源导出任务")
    public CommonResult<TaskSummary> submitSources(
            @Valid @RequestBody ExchangeSourceSubmitRequest request) {
        return success(applicationService.submitExchangeSources(request));
    }

    /** 冻结汇率规则筛选条件并创建后台任务。 */
    @PostMapping("/rules/export-task")
    @RequiresPermission("exchange:rule:export")
    @OperationLog(moduleName = "汇率规则管理", businessType = OperationTypeConstants.EXPORT,
            operation = "创建汇率规则导出任务")
    public CommonResult<TaskSummary> submitRules(
            @Valid @RequestBody ExchangeRuleSubmitRequest request) {
        return success(applicationService.submitExchangeRules(request));
    }

    /** 冻结汇率波动筛选条件并创建后台任务。 */
    @PostMapping("/volatility/export-task")
    @RequiresPermission("exchange:volatility:export")
    @OperationLog(moduleName = "汇率波动", businessType = OperationTypeConstants.EXPORT,
            operation = "创建汇率波动导出任务")
    public CommonResult<TaskSummary> submitVolatility(
            @Valid @RequestBody ExchangeVolatilitySubmitRequest request) {
        return success(applicationService.submitExchangeVolatility(request));
    }

    /** 冻结汇率预警规则筛选条件并创建后台任务。 */
    @PostMapping("/alert-rules/export-task")
    @RequiresPermission("exchange:alert-rule:export")
    @OperationLog(moduleName = "汇率预警规则", businessType = OperationTypeConstants.EXPORT,
            operation = "创建汇率预警规则导出任务")
    public CommonResult<TaskSummary> submitAlertRules(
            @Valid @RequestBody ExchangeAlertRuleSubmitRequest request) {
        return success(applicationService.submitExchangeAlertRules(request));
    }

    /** 冻结汇率预警事件筛选条件并创建后台任务。 */
    @PostMapping("/alert-events/export-task")
    @RequiresPermission("exchange:alert-event:export")
    @OperationLog(moduleName = "汇率预警事件", businessType = OperationTypeConstants.EXPORT,
            operation = "创建汇率预警事件导出任务")
    public CommonResult<TaskSummary> submitAlertEvents(
            @Valid @RequestBody ExchangeAlertEventSubmitRequest request) {
        return success(applicationService.submitExchangeAlertEvents(request));
    }
}
