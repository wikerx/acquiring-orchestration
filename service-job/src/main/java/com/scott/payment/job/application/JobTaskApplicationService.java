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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Task Application 服务契约，位于 service-job 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class JobTaskApplicationService {

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final JobTaskService jobTaskService;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final JobDispatchService jobDispatchService;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final JobHandlerRegistry jobHandlerRegistry;
    /**
     * 任务调度对象转换器。
     */
    private final JobSchedulerConverter jobSchedulerConverter;

    /**
     * 创建任务管理应用服务。
     *
     * @param jobTaskService     任务领域服务
     * @param jobDispatchService 任务分发服务
     * @param jobHandlerRegistry 处理器注册中心
     * @param jobSchedulerConverter 任务调度对象转换器
     */
    public JobTaskApplicationService(JobTaskService jobTaskService,
                                     JobDispatchService jobDispatchService,
                                     JobHandlerRegistry jobHandlerRegistry,
                                     JobSchedulerConverter jobSchedulerConverter) {
        this.jobTaskService = jobTaskService;
        this.jobDispatchService = jobDispatchService;
        this.jobHandlerRegistry = jobHandlerRegistry;
        this.jobSchedulerConverter = jobSchedulerConverter;
    }

    /**
     * 查询白名单处理器列表。
     *
     * @return 处理器选项列表
     */
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
     */
    public List<JobHandlerOptionResponse> listHandlers() {
        return jobHandlerRegistry.listDescriptors().stream()
                .map(jobSchedulerConverter::toHandlerOption)
                .toList();
    }

    /**
     * 分页查询任务。
     *
     * @param request 查询条件
     * @return 任务分页结果
     */
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public PageResult<JobTaskResponse> pageTasks(JobTaskQueryRequest request) {
        PageResult<SysJobTaskDO> pageResult = jobTaskService.pageTasks(request);
        return PageResult.of(
                pageResult.getTotal(),
                pageResult.getPageNo(),
                pageResult.getPageSize(),
                pageResult.getRecords().stream()
                        .map(jobSchedulerConverter::toTaskResponse)
                        .toList()
        );
    }

    /**
     * 新增任务。
     *
     * @param request 保存请求
     * @return 任务响应
     */
    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public JobTaskResponse createTask(JobTaskSaveRequest request) {
        return jobSchedulerConverter.toTaskResponse(jobTaskService.createTask(request));
    }

    /**
     * 更新任务。
     *
     * @param taskId  任务主键
     * @param request 保存请求
     * @return 任务响应
     */
    /**
     * 更新收单支付数据，保持已有记录、状态和审计字段的一致性。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public JobTaskResponse updateTask(Long taskId, JobTaskSaveRequest request) {
        return jobSchedulerConverter.toTaskResponse(jobTaskService.updateTask(taskId, request));
    }

    /**
     * 切换任务状态。
     *
     * @param taskId   任务主键
     * @param status   目标状态
     * @param operator 操作人
     * @return 任务响应
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param status 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param operator 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    public JobTaskResponse changeStatus(Long taskId, String status, String operator) {
        return jobSchedulerConverter.toTaskResponse(jobTaskService.changeStatus(taskId, status, operator));
    }

    /**
     * 手动执行一次任务。
     *
     * @param taskId   任务主键
     * @param request  手动触发请求
     * @return 执行批次号
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
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
    /**
     * 删除收单支付数据，按业务规则处理引用校验和删除边界。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param operator 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    public void deleteTask(Long taskId, String operator) {
        jobTaskService.deleteTask(taskId, operator);
    }
}
