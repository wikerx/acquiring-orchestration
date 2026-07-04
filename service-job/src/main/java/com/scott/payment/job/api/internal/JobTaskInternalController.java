package com.scott.payment.job.api.internal;

import com.scott.payment.component.core.model.CommonResult;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.job.api.internal.dto.JobHandlerOptionResponse;
import com.scott.payment.job.api.internal.dto.JobManualTriggerRequest;
import com.scott.payment.job.api.internal.dto.JobTaskQueryRequest;
import com.scott.payment.job.api.internal.dto.JobTaskResponse;
import com.scott.payment.job.api.internal.dto.JobTaskSaveRequest;
import com.scott.payment.job.application.JobTaskApplicationService;
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
 * @classname : JobTaskInternalController
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 调度中心任务管理内部控制器
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskInternalController
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Task Internal 管理接口，位于 service-job 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@RestController
@RequestMapping("/internal/job/tasks")
public class JobTaskInternalController {

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final JobTaskApplicationService jobTaskApplicationService;

    /**
     * 创建内部任务管理控制器。
     *
     * @param jobTaskApplicationService 任务应用服务
     */
    public JobTaskInternalController(JobTaskApplicationService jobTaskApplicationService) {
        this.jobTaskApplicationService = jobTaskApplicationService;
    }

    /**
     * 查询任务处理器白名单列表。
     *
     * @return 处理器选项列表
     */
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
     */
    @GetMapping("/handlers")
    public CommonResult<List<JobHandlerOptionResponse>> listHandlers() {
        return success(jobTaskApplicationService.listHandlers());
    }

    /**
     * 分页查询任务定义。
     *
     * @param request 查询条件
     * @return 任务分页列表
     */
    @PostMapping("/search")
    public CommonResult<PageResult<JobTaskResponse>> pageTasks(@RequestBody(required = false) @Valid JobTaskQueryRequest request) {
        return success(jobTaskApplicationService.pageTasks(request));
    }

    /**
     * 新增任务定义。
     *
     * @param request 任务保存请求
     * @return 保存结果
     */
    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping
    public CommonResult<JobTaskResponse> createTask(@RequestBody @Valid JobTaskSaveRequest request) {
        return success(jobTaskApplicationService.createTask(request));
    }

    /**
     * 更新任务定义。
     *
     * @param taskId   任务主键
     * @param request  任务保存请求
     * @return 更新结果
     */
    /**
     * 更新收单支付数据，保持已有记录、状态和审计字段的一致性。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{taskId}")
    public CommonResult<JobTaskResponse> updateTask(@PathVariable("taskId") Long taskId,
                                                    @RequestBody @Valid JobTaskSaveRequest request) {
        return success(jobTaskApplicationService.updateTask(taskId, request));
    }

    /**
     * 切换任务状态。
     *
     * @param taskId    任务主键
     * @param status    目标状态
     * @param operator  操作人
     * @return 更新结果
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param status 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param operator 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PutMapping("/{taskId}/status")
    public CommonResult<JobTaskResponse> changeStatus(@PathVariable("taskId") Long taskId,
                                                      @RequestParam("status") String status,
                                                      @RequestParam("operator") String operator) {
        return success(jobTaskApplicationService.changeStatus(taskId, status, operator));
    }

    /**
     * 手动执行一次任务。
     *
     * @param taskId   任务主键
     * @param request  执行请求
     * @return 本次执行生成的 runId
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @PostMapping("/{taskId}/trigger")
    public CommonResult<String> trigger(@PathVariable("taskId") Long taskId,
                                        @RequestBody @Valid JobManualTriggerRequest request) {
        return success(jobTaskApplicationService.trigger(taskId, request));
    }

    /**
     * 删除任务定义。
     *
     * @param taskId   任务主键
     * @param operator 操作人
     * @return 删除结果
     */
    /**
     * 删除收单支付数据，按业务规则处理引用校验和删除边界。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param operator 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @DeleteMapping("/{taskId}")
    public CommonResult<Void> deleteTask(@PathVariable("taskId") Long taskId,
                                         @RequestParam("operator") String operator) {
        jobTaskApplicationService.deleteTask(taskId, operator);
        return success();
    }
}
