package com.scott.payment.component.job.executor;

import com.scott.payment.component.job.model.JobExecuteResult;

import java.util.concurrent.CompletableFuture;

/**
 * 调度中心异步任务处理器协议
 */
public interface AsyncJobHandler extends JobHandler {

    /**
     * 异步执行任务。
     *
     * @param context 调度执行上下文
     * @return 异步结果 Future
     */
    CompletableFuture<JobExecuteResult> executeAsync(JobExecuteContext context);

    /**
     * 同步入口默认返回“已受理”，真正结果由异步回调补写。
     *
     * @param context 调度执行上下文
     * @return 已受理结果
     */
    @Override
    default JobExecuteResult execute(JobExecuteContext context) {
        return JobExecuteResult.accepted("async job accepted");
    }
}
