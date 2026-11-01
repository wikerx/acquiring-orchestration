package com.scott.payment.job.application;

import com.scott.payment.job.api.internal.dto.JobExecutorNodeResponse;
import com.scott.payment.job.converter.JobSchedulerConverter;
import com.scott.payment.job.service.JobExecutorNodeService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 任务执行器节点应用服务
 */
@Service
public class JobExecutorNodeApplicationService {

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
