package com.sinopay.payment.component.job.handler;

import com.sinopay.payment.component.job.model.JobExecuteResult;

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

    public abstract JobExecuteResult execute(String parameter);
}

