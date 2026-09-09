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
@RestController
@RequestMapping("/internal/job/tasks")
public class JobTaskInternalController {

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
    @DeleteMapping("/{taskId}")
    public CommonResult<Void> deleteTask(@PathVariable("taskId") Long taskId,
                                         @RequestParam("operator") String operator) {
        jobTaskApplicationService.deleteTask(taskId, operator);
        return success();
    }
}
