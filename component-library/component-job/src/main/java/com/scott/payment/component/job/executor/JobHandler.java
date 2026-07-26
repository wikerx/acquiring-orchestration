package com.scott.payment.component.job.executor;

import com.scott.payment.component.job.model.JobExecuteResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobHandler
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 调度中心统一任务处理器协议
 * @status : create
 */
public interface JobHandler {

    /**
     * 返回任务处理器描述。
     *
     * @return 处理器描述
     */
    JobHandlerDescriptor descriptor();

    /**
     * 执行任务。
     *
     * @param context 调度执行上下文
     * @return 任务执行结果
     */
    JobExecuteResult execute(JobExecuteContext context);
}
