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
 * @description : ShardingTablePreCreateService 服务契约，用于声明业务能力、调用边界和返回结果约束，位于 调度任务服务层，输入输出边界由所在包和公开方法契约限定。
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
