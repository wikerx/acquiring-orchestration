package com.scott.payment.admin.api.export;

import com.scott.payment.admin.application.export.AdminExportTaskApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SettlementReserveSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SettlementTransactionSubmitRequest;
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
 * 管理端结算明细异步导出提交接口，只冻结查询和数据范围，不修改结算事实。
 */
@RestController
@RequestMapping("/admin/settlement")
public class AdminSettlementExportTaskController {
    private final AdminExportTaskApplicationService applicationService;

    /**
     * 创建结算明细异步导出提交接口。
     */
    public AdminSettlementExportTaskController(AdminExportTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 冻结结算交易明细查询并创建后台导出任务。
     */
    @PostMapping("/result-items/export-task")
    @RequiresPermission("settlement:result-item:export")
    @OperationLog(moduleName = "结算交易明细", businessType = OperationTypeConstants.EXPORT,
            operation = "创建结算交易明细导出任务")
    public CommonResult<TaskSummary> submitTransactions(
            @Valid @RequestBody SettlementTransactionSubmitRequest request) {
        return success(applicationService.submitSettlementTransactions(request));
    }

    /**
     * 冻结保证金结算明细查询并创建后台导出任务。
     */
    @PostMapping("/reserve-items/export-task")
    @RequiresPermission("settlement:reserve-item:export")
    @OperationLog(moduleName = "保证金结算明细", businessType = OperationTypeConstants.EXPORT,
            operation = "创建保证金结算明细导出任务")
    public CommonResult<TaskSummary> submitReserves(
            @Valid @RequestBody SettlementReserveSubmitRequest request) {
        return success(applicationService.submitSettlementReserves(request));
    }
}
