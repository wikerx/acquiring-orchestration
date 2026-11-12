package com.scott.payment.admin.api.export;

import com.scott.payment.admin.application.export.AdminExportTaskApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SecurityInterceptEventSubmitRequest;
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

/** 管理端安全事件异步导出提交接口，原同步导出路径继续用于兼容。 */
@RestController
@RequestMapping("/admin/security/intercept-events")
public class AdminSecurityExportTaskController {
    private final AdminExportTaskApplicationService applicationService;

    /** 创建安全事件异步导出提交接口。 */
    public AdminSecurityExportTaskController(AdminExportTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /** 冻结安全事件查询和当前账号数据范围并创建后台任务。 */
    @PostMapping("/export-task")
    @RequiresPermission("security:intercept-event:export")
    @OperationLog(moduleName = "安全拦截事件", businessType = OperationTypeConstants.EXPORT,
            operation = "创建安全拦截事件导出任务")
    public CommonResult<TaskSummary> submit(
            @Valid @RequestBody SecurityInterceptEventSubmitRequest request) {
        return success(applicationService.submitSecurityInterceptEvents(request));
    }
}
