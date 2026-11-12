package com.scott.payment.admin.api.export;

import com.scott.payment.admin.application.export.AdminExportTaskApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.JobRunLogSubmitRequest;
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
 * 管理端监控数据异步导出提交接口，不改变任务日志查询和清理状态机。
 */
@RestController
@RequestMapping("/admin/monitor/job-log")
public class AdminMonitorExportTaskController {
    private final AdminExportTaskApplicationService applicationService;

    /**
     * 创建任务运行日志异步导出提交接口。
     *
     * @param applicationService 统一异步导出编排服务
     */
    public AdminMonitorExportTaskController(AdminExportTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 冻结任务日志公开筛选条件并创建后台导出任务。
     *
     * @param request 浏览器提交的幂等键和公开查询条件
     * @return 已创建或幂等命中的导出任务摘要
     */
    @PostMapping("/export-task")
    @RequiresPermission("monitor:jobLog:export")
    @OperationLog(moduleName = "任务调度", businessType = OperationTypeConstants.EXPORT,
            operation = "创建任务运行日志导出任务")
    public CommonResult<TaskSummary> submitJobRunLogs(
            @Valid @RequestBody JobRunLogSubmitRequest request) {
        return success(applicationService.submitJobRunLogs(request));
    }
}
