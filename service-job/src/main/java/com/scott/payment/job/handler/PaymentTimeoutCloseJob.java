package com.scott.payment.job.handler;

import com.scott.payment.component.job.handler.AbstractJobHandler;
import com.scott.payment.component.job.model.JobExecuteResult;
import lombok.extern.slf4j.Slf4j;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : PaymentTimeoutCloseJob
 * @date : 2026-05-28 10:28
 * @email : scott_x@163.com
 * @description : 支付超时关单任务处理器
 * @status : create
 */
@Slf4j
public class PaymentTimeoutCloseJob extends AbstractJobHandler {

    @Override
    public JobExecuteResult execute(String parameter) {
        log.info("Execute payment timeout close job, parameter: {}", parameter);
        return JobExecuteResult.success();
    }
}
