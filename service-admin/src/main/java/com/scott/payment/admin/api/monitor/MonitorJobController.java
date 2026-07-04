package com.scott.payment.admin.api.monitor;

import com.scott.payment.admin.application.monitor.AdminJobSchedulerApplicationService;
import com.scott.payment.admin.dto.monitor.JobHandlerOptionResponse;
import com.scott.payment.admin.dto.monitor.JobManualTriggerRequest;
import com.scott.payment.admin.dto.monitor.JobTaskQueryRequest;
import com.scott.payment.admin.dto.monitor.JobTaskResponse;
import com.scott.payment.admin.dto.monitor.JobTaskSaveRequest;
import com.scott.payment.component.core.auth.InternalAuthAccount;
import com.scott.payment.component.core.auth.InternalAuthContextHolder;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.web.auth.annotation.RequiresPermission;
import com.scott.payment.component.web.operation.annotation.OperationLog;
import com.scott.payment.component.web.operation.constant.OperationTypeConstants;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static com.scott.payment.component.core.model.CommonResult.success;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorJobController
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务调度控制器
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MonitorJobController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 监控治理Monitor Job 管理接口，位于 service-admin 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/admin/monitor/job")
public class MonitorJobController {

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final AdminJobSchedulerApplicationService adminJobSchedulerApplicationService;

    /**
     * 创建任务调度控制器。
     *
     * @param adminJobSchedulerApplicationService 任务调度应用服务
     */
    public MonitorJobController(AdminJobSchedulerApplicationService adminJobSchedulerApplicationService) {
        this.adminJobSchedulerApplicationService = adminJobSchedulerApplicationService;
    }

    /**
     * 查询任务处理器白名单。
     *
     * <p>该接口属于任务调度页面初始化所需的基础查询数据。
     * 只要账号具备任务调度菜单查看权限，就应该允许加载处理器选项；
     * 具体的新增、编辑、执行、删除操作仍由后续接口各自的按钮权限控制。</p>
     *
     * @return 处理器列表
     */
    /**
     * 处理监控治理业务流程，维护关键状态和异常边界。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/handlers")
    @RequiresPermission("monitor:job:list")
    public CommonResult<List<JobHandlerOptionResponse>> handlers() {
        return success(adminJobSchedulerApplicationService.listHandlers());
    }

    /**
     * 分页查询任务定义。
     *
     * @param request 查询条件
     * @return 任务分页结果
     */
    @PostMapping("/search")
    @RequiresPermission("monitor:job:list")
    @OperationLog(moduleName = "任务调度", businessType = OperationTypeConstants.QUERY, operation = "分页查询任务定义")
    public CommonResult<PageResult<JobTaskResponse>> search(@RequestBody(required = false) JobTaskQueryRequest request) {
        return success(adminJobSchedulerApplicationService.pageTasks(request));
    }

    /**
     * 新增任务定义。
     *
     * @param request 保存请求
     * @return 任务响应
     */
    /**
     * 创建或保存监控治理数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping
    @RequiresPermission("monitor:job:add")
    @OperationLog(moduleName = "任务调度", businessType = OperationTypeConstants.CREATE, operation = "新增任务定义")
    public CommonResult<JobTaskResponse> create(@RequestBody @Valid JobTaskSaveRequest request) {
        return success(adminJobSchedulerApplicationService.createTask(request, currentOperatorName()));
    }

    /**
     * 更新任务定义。
     *
     * @param taskId  任务主键
     * @param request 保存请求
     * @return 任务响应
     */
    /**
     * 更新监控治理数据，保持已有记录、状态和审计字段的一致性。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{taskId}")
    @RequiresPermission("monitor:job:edit")
    @OperationLog(moduleName = "任务调度", businessType = OperationTypeConstants.UPDATE, operation = "更新任务定义")
    public CommonResult<JobTaskResponse> update(@PathVariable("taskId") Long taskId,
                                                @RequestBody @Valid JobTaskSaveRequest request) {
        return success(adminJobSchedulerApplicationService.updateTask(taskId, request, currentOperatorName()));
    }

    /**
     * 切换任务状态。
     *
     * @param taskId 任务主键
     * @param status 目标状态
     * @return 任务响应
     */
    /**
     * 执行监控治理相关处理，保持当前层级的职责边界和返回语义。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param status 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{taskId}/status")
    @RequiresPermission("monitor:job:list")
    @OperationLog(moduleName = "任务调度", businessType = OperationTypeConstants.UPDATE, operation = "切换任务状态")
    public CommonResult<JobTaskResponse> changeStatus(@PathVariable("taskId") Long taskId,
                                                      @RequestParam("status") String status) {
        ensureStatusPermission(status);
        return success(adminJobSchedulerApplicationService.changeStatus(taskId, status, currentOperatorName()));
    }

    /**
     * 手动执行一次任务。
     *
     * @param taskId  任务主键
     * @param request 执行请求
     * @return 执行批次号
     */
    @PostMapping("/{taskId}/trigger")
    @RequiresPermission("monitor:job:run")
    @OperationLog(moduleName = "任务调度", businessType = OperationTypeConstants.UPDATE, operation = "手动执行任务")
    public CommonResult<String> trigger(@PathVariable("taskId") Long taskId,
                                        @RequestBody(required = false) JobManualTriggerRequest request) {
        JobManualTriggerRequest triggerRequest = request == null ? new JobManualTriggerRequest() : request;
        return success(adminJobSchedulerApplicationService.trigger(
                taskId,
                triggerRequest,
                currentOperatorId(),
                currentOperatorName()
        ));
    }

    /**
     * 删除任务定义。
     *
     * @param taskId 任务主键
     * @return 删除结果
     */
    /**
     * 删除监控治理数据，按业务规则处理引用校验和删除边界。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @DeleteMapping("/{taskId}")
    @RequiresPermission("monitor:job:remove")
    @OperationLog(moduleName = "任务调度", businessType = OperationTypeConstants.DELETE, operation = "删除任务定义")
    public CommonResult<Void> delete(@PathVariable("taskId") Long taskId) {
        adminJobSchedulerApplicationService.deleteTask(taskId, currentOperatorName());
        return success();
    }

    /**
     * 获取当前操作人 ID。
     *
     * @return 操作人 ID
     */
    private String currentOperatorId() {
        InternalAuthAccount account = getRequiredAccount();
        Long operatorId = account.getAccountId() == null ? account.getUserId() : account.getAccountId();
        return operatorId == null ? account.getLoginAccount() : String.valueOf(operatorId);
    }

    /**
     * 获取当前操作人名称。
     *
     * @return 操作人名称
     */
    private String currentOperatorName() {
        InternalAuthAccount account = getRequiredAccount();
        if (account.getRealName() != null && !account.getRealName().isBlank()) {
            return account.getRealName();
        }
        return account.getLoginAccount();
    }

    /**
     * 获取当前登录账号上下文。
     *
     * @return 当前登录账号
     */
    private InternalAuthAccount getRequiredAccount() {
        InternalAuthAccount account = InternalAuthContextHolder.get();
        if (account == null) {
            throw new IllegalStateException("internal auth account is missing");
        }
        return account;
    }

    /**
     * 按目标状态校验启停按钮权限。
     *
     * <p>任务状态切换仍复用同一个接口，但前端菜单权限需要拆分为
     * “启用”和“停用”两个独立按钮标识，因此这里补充运行时二次校验。</p>
     *
     * @param status 目标状态
     */
    private void ensureStatusPermission(String status) {
        InternalAuthAccount account = getRequiredAccount();
        List<String> permissions = account.getPermissions();
        if (permissions.contains("*:*:*")) {
            return;
        }
        String requiredPermission = "ENABLED".equalsIgnoreCase(status) ? "monitor:job:start" : "monitor:job:stop";
        if (!permissions.contains(requiredPermission)) {
            throw new ServiceException(ApiResultEnum.FORBIDDEN);
        }
    }
}
