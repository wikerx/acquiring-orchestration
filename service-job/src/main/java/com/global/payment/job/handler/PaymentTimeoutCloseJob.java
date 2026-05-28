package com.global.payment.job.handler;

import com.global.payment.component.job.handler.AbstractJobHandler;
import com.global.payment.component.job.model.JobExecuteResult;

public class PaymentTimeoutCloseJob extends AbstractJobHandler {

    @Override
    public JobExecuteResult execute(String parameter) {
        return JobExecuteResult.success();
    }
}

