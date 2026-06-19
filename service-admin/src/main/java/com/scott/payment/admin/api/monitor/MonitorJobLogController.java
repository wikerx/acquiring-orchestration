package com.scott.payment.admin.api.monitor;

import com.scott.payment.admin.application.monitor.AdminJobSchedulerApplicationService;
import com.scott.payment.admin.dto.monitor.JobRunLogQueryRequest;
import com.scott.payment.admin.dto.monitor.JobRunLogResponse;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorJobLogController
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务执行日志控制器
 * @status : create
 */

@RestController
@RequestMapping("/admin/monitor/job-log")
public class MonitorJobLogController {

    private final AdminJobSchedulerApplicationService adminJobSchedulerApplicationService;

    /**
     * 创建任务执行日志控制器。
     *
     * @param adminJobSchedulerApplicationService 任务调度应用服务
     */
    public MonitorJobLogController(AdminJobSchedulerApplicationService adminJobSchedulerApplicationService) {
        this.adminJobSchedulerApplicationService = adminJobSchedulerApplicationService;
    }

    /**
     * 分页查询任务执行日志。
     *
     * @param request 查询条件
     * @return 日志分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("monitor:jobLog:list")
    @OperationLog(moduleName = "任务调度", businessType = OperationTypeConstants.QUERY, operation = "分页查询任务执行日志")
    public CommonResult<PageResult<JobRunLogResponse>> search(@RequestBody(required = false) JobRunLogQueryRequest request) {
        return success(adminJobSchedulerApplicationService.pageRunLogs(request));
    }

    /**
     * 导出任务执行日志。
     *
     * @param request 查询条件
     * @param response HTTP 响应
     */
    @PostMapping("/export")
    @RequiresPermission("monitor:jobLog:export")
    @OperationLog(moduleName = "任务调度", businessType = OperationTypeConstants.EXPORT, operation = "导出任务执行日志")
    public void export(@RequestBody(required = false) JobRunLogQueryRequest request,
                       HttpServletResponse response) {
        adminJobSchedulerApplicationService.exportRunLogs(request, currentOperatorName(), response);
    }

    /**
     * 删除单条任务执行日志。
     *
     * @param id 日志主键
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @RequiresPermission("monitor:jobLog:remove")
    @OperationLog(moduleName = "任务调度", businessType = OperationTypeConstants.DELETE, operation = "删除任务执行日志")
    public CommonResult<Void> remove(@PathVariable("id") Long id) {
        adminJobSchedulerApplicationService.removeRunLog(id);
        return success();
    }

    /**
     * 按条件清空任务执行日志。
     *
     * @param request 查询条件
     * @return 删除数量
     */
    @PostMapping("/clean")
    @RequiresPermission("monitor:jobLog:clean")
    @OperationLog(moduleName = "任务调度", businessType = OperationTypeConstants.DELETE, operation = "清空任务执行日志")
    public CommonResult<Integer> clean(@RequestBody(required = false) JobRunLogQueryRequest request) {
        return success(adminJobSchedulerApplicationService.cleanRunLogs(request));
    }

    /**
     * 获取当前操作人名称。
     *
     * @return 操作人名称
     */
    private String currentOperatorName() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            return "admin";
        }
        if (account.getRealName() != null && !account.getRealName().isBlank()) {
            return account.getRealName();
        }
        return account.getLoginAccount();
    }
}
