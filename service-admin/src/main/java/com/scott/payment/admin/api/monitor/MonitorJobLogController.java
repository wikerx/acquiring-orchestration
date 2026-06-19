package com.scott.payment.admin.api.monitor;

import com.scott.payment.admin.application.monitor.AdminJobSchedulerApplicationService;
import com.scott.payment.admin.dto.monitor.JobRunLogQueryRequest;
import com.scott.payment.admin.dto.monitor.JobRunLogResponse;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
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
}
