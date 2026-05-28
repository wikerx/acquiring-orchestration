package com.sinopay.payment.job.handler;

import com.sinopay.payment.component.job.handler.AbstractJobHandler;
import com.sinopay.payment.component.job.model.JobExecuteResult;

public class PaymentTimeoutCloseJob extends AbstractJobHandler {

    @Override
    public JobExecuteResult execute(String parameter) {
        return JobExecuteResult.success();
    }
}

