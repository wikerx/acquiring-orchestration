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

    /**
     * job Task Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final JobTaskService jobTaskService;
    /**
     * job Dispatch Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final JobDispatchService jobDispatchService;
    /**
     * job Handler Registry 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
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
