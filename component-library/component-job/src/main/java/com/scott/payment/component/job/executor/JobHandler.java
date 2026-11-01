package com.scott.payment.component.job.executor;

import com.scott.payment.component.job.model.JobExecuteResult;

/**
 * 调度中心统一任务处理器协议
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
