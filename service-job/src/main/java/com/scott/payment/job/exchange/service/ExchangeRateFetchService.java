package com.scott.payment.job.exchange.service;

import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.ExchangeRateFetchRequest;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.ExchangeRateFetchResult;


/**
 * 汇率汇率fetch服务契约，位于 调度任务服务，声明该业务能力的输入、返回结果和异常边界，由实现类保持一致。
 */
public interface ExchangeRateFetchService {

    /**
     * 执行指定汇率源拉取。
     *
     * @param request 拉取请求
     * @param context 任务上下文
     * @return 拉取结果
     */
    ExchangeRateFetchResult fetch(ExchangeRateFetchRequest request, JobExecuteContext context);
}
