package com.scott.payment.admin.api.export;

import com.scott.payment.admin.application.export.AdminExportTaskApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FeeReviewSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FeeSimulationSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.FeeTemplateSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.MerchantFeeSubmitRequest;
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
 * 管理端费用配置与试算异步导出提交入口。
 */
@RestController
@RequestMapping("/admin/fees")
public class AdminFeeExportTaskController {
    private final AdminExportTaskApplicationService applicationService;

    /** 创建费用域异步导出提交入口。 */
    public AdminFeeExportTaskController(AdminExportTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 创建费用模板异步导出任务。 */
    @PostMapping("/templates/export-task")
    @RequiresPermission("fee:template:export")
    @OperationLog(moduleName = "费用模板", businessType = OperationTypeConstants.EXPORT,
            operation = "创建费用模板导出任务")
    public CommonResult<TaskSummary> submitTemplates(
            @Valid @RequestBody FeeTemplateSubmitRequest request) {
        return success(applicationService.submitFeeTemplates(request));
    }

    /** 创建商户费率异步导出任务。 */
    @PostMapping("/merchants/export-task")
    @RequiresPermission("fee:merchant:export")
    @OperationLog(moduleName = "商户费率", businessType = OperationTypeConstants.EXPORT,
            operation = "创建商户费率导出任务")
    public CommonResult<TaskSummary> submitMerchantFees(
            @Valid @RequestBody MerchantFeeSubmitRequest request) {
        return success(applicationService.submitMerchantFees(request));
    }

    /** 创建费率审核异步导出任务。 */
    @PostMapping("/reviews/export-task")
    @RequiresPermission("fee:review:export")
    @OperationLog(moduleName = "费率审核", businessType = OperationTypeConstants.EXPORT,
            operation = "创建费率审核导出任务")
    public CommonResult<TaskSummary> submitReviews(
            @Valid @RequestBody FeeReviewSubmitRequest request) {
        return success(applicationService.submitFeeReviews(request));
    }

    /** 创建费用试算记录异步导出任务。 */
    @PostMapping("/simulation-records/export-task")
    @RequiresPermission("fee:simulation:record:export")
    @OperationLog(moduleName = "费用试算", businessType = OperationTypeConstants.EXPORT,
            operation = "创建费用试算记录导出任务")
    public CommonResult<TaskSummary> submitSimulations(
            @Valid @RequestBody FeeSimulationSubmitRequest request) {
        return success(applicationService.submitFeeSimulations(request));
    }
}
