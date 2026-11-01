package com.scott.payment.job.exchange.service;

import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.ExchangeRateFetchRequest;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.ExchangeRateFetchResult;


/**
 * 汇率抓取服务契约，声明外部报价抓取、持久化和业务汇率生成入口。
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
