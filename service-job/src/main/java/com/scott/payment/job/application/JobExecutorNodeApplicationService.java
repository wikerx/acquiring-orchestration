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
     * job Executor Node Service 依赖，用于 Job Executor Node Application Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
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
