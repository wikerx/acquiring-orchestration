package com.scott.payment.admin.api.export;

import com.scott.payment.admin.application.export.AdminExportTaskApplicationService;
import com.scott.payment.admin.application.risk.AdminRiskManagementApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RiskConfigSubmitRequest;
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
 * 管理端风控配置异步导出提交入口，提交时同时校验基础访问权限和功能级导出权限。
 */
@RestController
@RequestMapping("/admin/risk/export-tasks")
public class AdminRiskExportTaskController {
    private final AdminExportTaskApplicationService exportTaskApplicationService;
    private final AdminRiskManagementApplicationService riskManagementApplicationService;

    /** 创建风控配置异步导出提交入口。 */
    public AdminRiskExportTaskController(
            AdminExportTaskApplicationService exportTaskApplicationService,
            AdminRiskManagementApplicationService riskManagementApplicationService) {
        this.exportTaskApplicationService = exportTaskApplicationService;
        this.riskManagementApplicationService = riskManagementApplicationService;
    }

    /**
     * 冻结受控功能和当前筛选条件并创建后台导出任务。
     */
    @PostMapping
    @RequiresPermission("risk:access")
    @OperationLog(moduleName = "收单风控", businessType = OperationTypeConstants.EXPORT,
            operation = "创建风控配置导出任务")
    public CommonResult<TaskSummary> submit(@Valid @RequestBody RiskConfigSubmitRequest request) {
        riskManagementApplicationService.ensureExportPermission(
                request.getModuleType(), request.getFunctionCode());
        return success(exportTaskApplicationService.submitRiskConfig(request));
    }
}
