package com.scott.payment.job.application;

import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.job.api.internal.dto.JobHandlerOptionResponse;
import com.scott.payment.job.api.internal.dto.JobManualTriggerRequest;
import com.scott.payment.job.api.internal.dto.JobTaskQueryRequest;
import com.scott.payment.job.api.internal.dto.JobTaskResponse;
import com.scott.payment.job.api.internal.dto.JobTaskSaveRequest;
import com.scott.payment.job.converter.JobSchedulerConverter;
import com.scott.payment.job.entity.SysJobTaskDO;
import com.scott.payment.job.executor.JobDispatchService;
import com.scott.payment.job.executor.JobHandlerRegistry;
import com.scott.payment.job.service.JobTaskService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskApplicationService
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 调度中心任务管理应用服务
 * @status : create
 */

@Service
public class JobTaskApplicationService {

    private final JobTaskService jobTaskService;
    private final JobDispatchService jobDispatchService;
    private final JobHandlerRegistry jobHandlerRegistry;

    /**
     * 创建任务管理应用服务。
     *
     * @param jobTaskService     任务领域服务
     * @param jobDispatchService 任务分发服务
     * @param jobHandlerRegistry 处理器注册中心
     */
    public JobTaskApplicationService(JobTaskService jobTaskService,
                                     JobDispatchService jobDispatchService,
                                     JobHandlerRegistry jobHandlerRegistry) {
        this.jobTaskService = jobTaskService;
        this.jobDispatchService = jobDispatchService;
        this.jobHandlerRegistry = jobHandlerRegistry;
    }

    /**
     * 查询白名单处理器列表。
     *
     * @return 处理器选项列表
     */
    public List<JobHandlerOptionResponse> listHandlers() {
        return jobHandlerRegistry.listDescriptors().stream()
                .map(JobSchedulerConverter.INSTANCE::toHandlerOption)
                .toList();
    }

    /**
     * 分页查询任务。
     *
     * @param request 查询条件
     * @return 任务分页结果
     */
    public PageResult<JobTaskResponse> pageTasks(JobTaskQueryRequest request) {
        PageResult<SysJobTaskDO> pageResult = jobTaskService.pageTasks(request);
        return PageResult.of(
                pageResult.getTotal(),
                pageResult.getPageNo(),
                pageResult.getPageSize(),
                pageResult.getRecords().stream()
                        .map(JobSchedulerConverter.INSTANCE::toTaskResponse)
                        .toList()
        );
    }

    /**
     * 新增任务。
     *
     * @param request 保存请求
     * @return 任务响应
     */
    public JobTaskResponse createTask(JobTaskSaveRequest request) {
        return JobSchedulerConverter.INSTANCE.toTaskResponse(jobTaskService.createTask(request));
    }

    /**
     * 更新任务。
     *
     * @param taskId  任务主键
     * @param request 保存请求
     * @return 任务响应
     */
    public JobTaskResponse updateTask(Long taskId, JobTaskSaveRequest request) {
        return JobSchedulerConverter.INSTANCE.toTaskResponse(jobTaskService.updateTask(taskId, request));
    }

    /**
     * 切换任务状态。
     *
     * @param taskId   任务主键
     * @param status   目标状态
     * @param operator 操作人
     * @return 任务响应
     */
    public JobTaskResponse changeStatus(Long taskId, String status, String operator) {
        return JobSchedulerConverter.INSTANCE.toTaskResponse(jobTaskService.changeStatus(taskId, status, operator));
    }

    /**
     * 手动执行一次任务。
     *
     * @param taskId   任务主键
     * @param request  手动触发请求
     * @return 执行批次号
     */
    public String trigger(Long taskId, JobManualTriggerRequest request) {
        return jobDispatchService.triggerManual(taskId, request);
    }

    /**
     * 删除任务。
     *
     * @param taskId   任务主键
     * @param operator 操作人
     */
    public void deleteTask(Long taskId, String operator) {
        jobTaskService.deleteTask(taskId, operator);
    }
}
