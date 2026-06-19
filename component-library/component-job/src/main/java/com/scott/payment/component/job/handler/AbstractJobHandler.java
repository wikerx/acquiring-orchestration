package com.scott.payment.component.job.handler;

import com.scott.payment.component.job.enums.JobExecuteModeEnum;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AbstractJobHandler
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 历史任务处理器兼容抽象基类
 * @status : create
 */

public abstract class AbstractJobHandler implements JobHandler {

    /**
     * 返回默认任务处理器描述。
     *
     * <p>历史任务类如果需要更准确的 handlerCode、任务分组或描述，应在具体实现中覆盖本方法。</p>
     *
     * @return 任务处理器描述
     */
    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.builder()
                .handlerCode(getClass().getSimpleName())
                .handlerName(getClass().getSimpleName())
                .jobGroup("system")
                .executeMode(JobExecuteModeEnum.SYNC)
                .description("兼容历史任务处理器")
                .allowManualTrigger(Boolean.TRUE)
                .allowConcurrent(Boolean.FALSE)
                .build();
    }

    /**
     * 适配新调度上下文到旧的字符串参数接口。
     *
     * @param context 调度执行上下文
     * @return 任务执行结果
     */
    @Override
    public JobExecuteResult execute(JobExecuteContext context) {
        return execute(context == null ? null : context.getParamsJson());
    }

    /**
     * 执行历史风格调度任务。
     *
     * @param parameter 调度平台传入的任务参数
     * @return 任务执行结果
     */
    public abstract JobExecuteResult execute(String parameter);
}
