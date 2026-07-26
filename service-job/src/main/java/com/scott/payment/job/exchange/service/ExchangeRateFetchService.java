package com.scott.payment.job.exchange.service;

import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.ExchangeRateFetchRequest;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.ExchangeRateFetchResult;


/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ExchangeRateFetchService
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : Exchange Rate Fetch Service 服务契约，位于 调度任务服务，声明当前业务能力的输入、返回结果和异常边界，由实现类保持一致。
 * @status : create
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
