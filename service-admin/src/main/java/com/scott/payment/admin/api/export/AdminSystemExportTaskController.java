package com.scott.payment.admin.api.export;

import com.scott.payment.admin.application.export.AdminExportTaskApplicationService;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.DepartmentSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.DictDataSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.DictTypeSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.HolidayCalendarSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.PostSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.TaskSummary;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SystemUserSubmitRequest;
import com.scott.payment.admin.dto.exporttask.AdminExportTaskDTOs.SystemConfigSubmitRequest;
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
 * 管理端组织、字典与日历异步导出提交入口。
 *
 * <p>接口只接受幂等键和页面公开筛选条件，账号、语言及数据范围全部从可信认证上下文冻结。</p>
 */
@RestController
@RequestMapping("/admin/system")
public class AdminSystemExportTaskController {
    private final AdminExportTaskApplicationService applicationService;

    /**
     * 创建系统管理异步导出提交入口。
     *
     * @param applicationService 统一异步导出编排服务
     */
    public AdminSystemExportTaskController(AdminExportTaskApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    /**
     * 创建部门异步导出任务，并由服务端冻结可信操作人及数据范围。
     */
    @PostMapping("/dept/export-task")
    @RequiresPermission("system:dept:export")
    @OperationLog(moduleName = "部门管理", businessType = OperationTypeConstants.EXPORT,
            operation = "创建部门导出任务")
    public CommonResult<TaskSummary> submitDepartments(
            @Valid @RequestBody DepartmentSubmitRequest request) {
        return success(applicationService.submitDepartments(request));
    }

    /**
     * 创建岗位异步导出任务，并冻结页面公开筛选条件。
     */
    @PostMapping("/post/export-task")
    @RequiresPermission("system:post:export")
    @OperationLog(moduleName = "岗位管理", businessType = OperationTypeConstants.EXPORT,
            operation = "创建岗位导出任务")
    public CommonResult<TaskSummary> submitPosts(@Valid @RequestBody PostSubmitRequest request) {
        return success(applicationService.submitPosts(request));
    }

    /**
     * 创建字典类型异步导出任务，并冻结页面公开筛选条件。
     */
    @PostMapping("/dicts/types/export-task")
    @RequiresPermission("system:dict:export")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.EXPORT,
            operation = "创建字典类型导出任务")
    public CommonResult<TaskSummary> submitDictTypes(
            @Valid @RequestBody DictTypeSubmitRequest request) {
        return success(applicationService.submitDictTypes(request));
    }

    /**
     * 创建字典数据异步导出任务，并冻结当前字典范围及公开筛选条件。
     */
    @PostMapping("/dicts/data/export-task")
    @RequiresPermission("system:dictData:export")
    @OperationLog(moduleName = "数据字典", businessType = OperationTypeConstants.EXPORT,
            operation = "创建字典数据导出任务")
    public CommonResult<TaskSummary> submitDictData(
            @Valid @RequestBody DictDataSubmitRequest request) {
        return success(applicationService.submitDictData(request));
    }

    /**
     * 创建指定自然年的节假日日历异步导出任务。
     */
    @PostMapping("/holiday-calendar/export-task")
    @RequiresPermission("system:calendar:export")
    @OperationLog(moduleName = "节假日日历", businessType = OperationTypeConstants.EXPORT,
            operation = "创建节假日日历导出任务")
    public CommonResult<TaskSummary> submitHolidayCalendar(
            @Valid @RequestBody HolidayCalendarSubmitRequest request) {
        return success(applicationService.submitHolidayCalendar(request));
    }

    /** 创建系统账户异步导出任务。 */
    @PostMapping("/users/export-task")
    @RequiresPermission("system:user:export")
    @OperationLog(moduleName = "用户管理", businessType = OperationTypeConstants.EXPORT,
            operation = "创建系统账户导出任务")
    public CommonResult<TaskSummary> submitSystemUsers(
            @Valid @RequestBody SystemUserSubmitRequest request) {
        return success(applicationService.submitSystemUsers(request));
    }

    /** 创建系统参数异步导出任务。 */
    @PostMapping("/configs/export-task")
    @RequiresPermission("system:config:export")
    @OperationLog(moduleName = "参数设置", businessType = OperationTypeConstants.EXPORT,
            operation = "创建系统参数导出任务")
    public CommonResult<TaskSummary> submitSystemConfigs(
            @Valid @RequestBody SystemConfigSubmitRequest request) {
        return success(applicationService.submitSystemConfigs(request));
    }
}
