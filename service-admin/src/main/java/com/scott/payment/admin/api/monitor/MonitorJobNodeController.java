package com.scott.payment.admin.api.monitor;

import com.scott.payment.admin.application.monitor.AdminJobSchedulerApplicationService;
import com.scott.payment.admin.dto.monitor.JobExecutorNodeResponse;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorJobNodeController
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务执行节点控制器
 * @status : create
 */

@RestController
@RequestMapping("/admin/monitor/job-node")
public class MonitorJobNodeController {

    private final AdminJobSchedulerApplicationService adminJobSchedulerApplicationService;

    /**
     * 创建任务执行节点控制器。
     *
     * @param adminJobSchedulerApplicationService 任务调度应用服务
     */
    public MonitorJobNodeController(AdminJobSchedulerApplicationService adminJobSchedulerApplicationService) {
        this.adminJobSchedulerApplicationService = adminJobSchedulerApplicationService;
    }

    /**
     * 查询执行节点列表。
     *
     * @return 执行节点列表
     */
    @GetMapping("/list")
    @RequiresPermission("monitor:jobNode:list")
    public CommonResult<List<JobExecutorNodeResponse>> list() {
        return success(adminJobSchedulerApplicationService.listNodes());
    }
}
