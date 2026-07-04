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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecutorNodeApplicationService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Executor Node Application 服务契约，位于 service-job 的应用编排层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class JobExecutorNodeApplicationService {

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private final JobExecutorNodeService jobExecutorNodeService;

    /**
     * 创建执行节点应用服务。
     *
     * @param jobExecutorNodeService 节点领域服务
     */
    public JobExecutorNodeApplicationService(JobExecutorNodeService jobExecutorNodeService) {
        this.jobExecutorNodeService = jobExecutorNodeService;
    }

    /**
     * 查询节点列表。
     *
     * @return 节点响应列表
     */
    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @return 处理后的业务结果或页面展示数据。
     */
    public List<JobExecutorNodeResponse> listNodes() {
        return jobExecutorNodeService.listNodes().stream()
                .map(JobSchedulerConverter.INSTANCE::toNodeResponse)
                .toList();
    }
}
