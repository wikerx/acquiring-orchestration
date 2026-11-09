package com.scott.payment.admin.api.export;

import com.scott.payment.admin.application.export.AdminExportTaskApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.WorkItemSubmitRequest;
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
 * 统一运营待办异步导出提交接口，保留原同步导出路径供兼容窗口使用。
 */
@RestController
@RequestMapping("/admin/operations/work-items")
public class AdminOperationalWorkItemExportController {
    private final AdminExportTaskApplicationService applicationService;

    /**
     * 创建统一待办异步导出提交入口。
     */
    public AdminOperationalWorkItemExportController(AdminExportTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 冻结当前操作人的查询与数据范围并创建后台导出任务。
     */
    @PostMapping("/export-task")
    @RequiresPermission("operations:work-item:export")
    @OperationLog(moduleName = "统一运营待办", businessType = OperationTypeConstants.EXPORT,
            operation = "创建待办导出任务")
    public CommonResult<TaskSummary> submit(@Valid @RequestBody WorkItemSubmitRequest request) {
        return success(applicationService.submitOperationalWorkItems(request));
    }
}
