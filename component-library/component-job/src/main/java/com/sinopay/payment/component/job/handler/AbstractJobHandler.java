package com.sinopay.payment.component.job.handler;

import com.sinopay.payment.component.job.model.JobExecuteResult;

public abstract class AbstractJobHandler {

    public abstract JobExecuteResult execute(String parameter);
}

