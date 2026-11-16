package com.scott.payment.admin.api.export;

import com.scott.payment.admin.application.export.AdminExportTaskApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.DatasourceSnapshotSubmitRequest;
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

/** 管理端数据源运行快照异步导出提交入口。 */
@RestController
@RequestMapping("/admin/monitor/datasource")
public class AdminDatasourceExportTaskController {
    private final AdminExportTaskApplicationService applicationService;

    /** 创建数据源快照异步导出提交入口。 */
    public AdminDatasourceExportTaskController(AdminExportTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 创建只读取服务端脱敏运行快照的后台导出任务。 */
    @PostMapping("/export-task")
    @RequiresPermission("monitor:datasource:export")
    @OperationLog(moduleName = "数据源监控", businessType = OperationTypeConstants.EXPORT,
            operation = "创建数据源监控快照导出任务")
    public CommonResult<TaskSummary> submit(
            @Valid @RequestBody DatasourceSnapshotSubmitRequest request) {
        return success(applicationService.submitDatasourceSnapshot(request));
    }
}
