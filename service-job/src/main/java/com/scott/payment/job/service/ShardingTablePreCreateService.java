package com.scott.payment.job.service;

import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateRequest;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateResult;


/**
 * 分表表precreate服务契约，位于 调度任务服务，声明该业务能力的输入、返回结果和异常边界，由实现类保持一致。
 */
public interface ShardingTablePreCreateService {

    /**
     * 执行分表物理表预创建。
     *
     * @param request 任务参数
     * @param context 任务执行上下文
     * @return 预创建结果
     */
    ShardingTablePreCreateResult preCreate(ShardingTablePreCreateRequest request, JobExecuteContext context);
}
