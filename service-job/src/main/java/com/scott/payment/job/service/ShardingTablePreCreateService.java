package com.scott.payment.job.service;

import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateRequest;
import com.scott.payment.job.dto.sharding.ShardingTablePreCreateResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateService
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 分表物理表预创建服务。 <p>负责读取 Nacos 分表配置、计算目标季度、检查模板表和物理表、执行 dryRun 或真实建表， 并登记物理表状态和批次日志。</p>
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
