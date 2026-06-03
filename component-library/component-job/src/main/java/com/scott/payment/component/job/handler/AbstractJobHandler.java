package com.scott.payment.component.job.handler;

import com.scott.payment.component.job.model.JobExecuteResult;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : AbstractJobHandler
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 任务处理器抽象基类
 * @status : create
 */
public abstract class AbstractJobHandler {

    /**
     * 执行调度任务。
     *
     * @param parameter 调度平台传入的任务参数
     * @return 任务执行结果
     */
    public abstract JobExecuteResult execute(String parameter);
}
