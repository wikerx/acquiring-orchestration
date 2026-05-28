package com.global.payment.component.job.handler;

import com.global.payment.component.job.model.JobExecuteResult;

public abstract class AbstractJobHandler {

    public abstract JobExecuteResult execute(String parameter);
}

