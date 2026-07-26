package com.scott.payment.job.application;

import com.scott.payment.job.api.internal.dto.JobExecutorNodeResponse;
import com.scott.payment.job.converter.JobSchedulerConverter;
import com.scott.payment.job.service.JobExecutorNodeService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecutorNodeApplicationService
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务执行器节点应用服务
 * @status : create
 */
@Service
public class JobExecutorNodeApplicationService {

    /**
     * job Executor Node Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final JobExecutorNodeService jobExecutorNodeService;
    /**
     * 任务调度对象转换器。
     */
    private final JobSchedulerConverter jobSchedulerConverter;

    /**
     * 创建执行节点应用服务。
     *
     * @param jobExecutorNodeService 节点领域服务
     * @param jobSchedulerConverter 任务调度对象转换器
     */
    public JobExecutorNodeApplicationService(JobExecutorNodeService jobExecutorNodeService,
                                             JobSchedulerConverter jobSchedulerConverter) {
        this.jobExecutorNodeService = jobExecutorNodeService;
        this.jobSchedulerConverter = jobSchedulerConverter;
    }

    /**
     * 查询节点列表。
     *
     * @return 节点响应列表
     */
    public List<JobExecutorNodeResponse> listNodes() {
        return jobExecutorNodeService.listNodes().stream()
                .map(jobSchedulerConverter::toNodeResponse)
                .toList();
    }
}
