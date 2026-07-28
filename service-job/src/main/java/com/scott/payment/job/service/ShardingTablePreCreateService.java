package com.scott.payment.job.service;

import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateRequest;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateResult;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateService
 * @date : 2026-06-21 22:32
 * @email : scott_x@163.com
 * @description : Sharding Table Pre Create Service 服务契约，位于 调度任务服务，声明当前业务能力的输入、返回结果和异常边界，由实现类保持一致。
 * @status : create
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
