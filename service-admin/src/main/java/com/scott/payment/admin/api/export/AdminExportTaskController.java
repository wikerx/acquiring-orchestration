package com.scott.payment.admin.api.export;

import com.scott.payment.admin.application.export.AdminExportTaskApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.RetryRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SearchRequestDTO;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * 管理端异步导出与下载中心接口，只允许操作当前账号自己创建的任务。
 */
@RestController
@RequestMapping("/admin/operations/export-tasks")
public class AdminExportTaskController {
    private final AdminExportTaskApplicationService applicationService;

    /**
     * 创建导出任务查询、详情、下载和重试入口。
     */
    public AdminExportTaskController(AdminExportTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 分页查询当前账号创建的导出任务。
     */
    @PostMapping("/search")
    @RequiresPermission("operations:export-task:list")
    public CommonResult<PageResult<TaskSummary>> search(
            @Valid @RequestBody(required = false) SearchRequestDTO request) {
        return success(applicationService.search(request));
    }

    /**
     * 查询当前账号拥有的导出任务详情。
     */
    @GetMapping("/{taskNo}")
    @RequiresPermission("operations:export-task:detail")
    public CommonResult<TaskSummary> detail(@PathVariable("taskNo") String taskNo) {
        return success(applicationService.detail(taskNo));
    }

    /**
     * 使用版本条件人工重试已失败的导出任务。
     */
    @PostMapping("/{taskNo}/retry")
    @RequiresPermission("operations:export-task:retry")
    @OperationLog(moduleName = "异步导出与下载中心", businessType = OperationTypeConstants.UPDATE,
            operation = "重试导出任务")
    public CommonResult<TaskSummary> retry(@PathVariable("taskNo") String taskNo,
                                           @Valid @RequestBody RetryRequest request) {
        return success(applicationService.retry(taskNo, request));
    }

    /**
     * 代理下载当前账号拥有且仍在有效期内的私有文件。
     */
    @GetMapping("/{taskNo}/download")
    @RequiresPermission("operations:export-task:download")
    @OperationLog(moduleName = "异步导出与下载中心", businessType = OperationTypeConstants.EXPORT,
            operation = "下载导出文件")
    public void download(@PathVariable("taskNo") String taskNo,
                         HttpServletRequest request,
                         HttpServletResponse response) {
        applicationService.download(taskNo, request, response);
    }
}
