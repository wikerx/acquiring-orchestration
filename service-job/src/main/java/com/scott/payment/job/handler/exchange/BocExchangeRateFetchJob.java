package com.scott.payment.job.handler.exchange;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.ExchangeRateFetchRequest;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.ExchangeRateFetchResult;
import com.scott.payment.job.exchange.service.ExchangeRateFetchService;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : BocExchangeRateFetchJob
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 汇率管理Boc Exchange Rate Fetch Job，位于 service-job 的任务调度层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
public class BocExchangeRateFetchJob implements JobHandler {

    /**
     * 汇率管理固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    public static final String HANDLER_CODE = "bocExchangeRateFetchJob";

    /**
     * 汇率管理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    private final ExchangeRateFetchService exchangeRateFetchService;

    /**
     * 构造中国银行汇率拉取任务处理器。
     *
     * @param exchangeRateFetchService 汇率源拉取服务
     */
    public BocExchangeRateFetchJob(ExchangeRateFetchService exchangeRateFetchService) {
        this.exchangeRateFetchService = exchangeRateFetchService;
    }

    /**
     * 声明任务处理器元数据，供任务调度中心发现和展示。
     *
     * @return 任务处理器描述
     */
    /**
     * 执行汇率管理相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(
                HANDLER_CODE,
                "中国银行汇率拉取任务",
                "exchange",
                "定时拉取中国银行外汇牌价并写入原始汇率记录"
        );
    }

    /**
     * 执行中国银行汇率拉取任务。
     *
     * @param context 任务执行上下文，包含运行 ID 和任务参数
     * @return 调度中心可识别的执行结果
     */
    /**
     * 执行汇率管理相关处理，保持当前层级的职责边界和返回语义。
     * @param context 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public JobExecuteResult execute(JobExecuteContext context) {
        if (context == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "job execute context is required");
        }
        ExchangeRateFetchRequest request = context.parseParams(ExchangeRateFetchRequest.class);
        ExchangeRateFetchResult result = exchangeRateFetchService.fetch(request, context);
        if ("FAILED".equals(result.getFetchStatus())) {
            return JobExecuteResult.failed(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), result.getErrorMessage());
        }
        return JobExecuteResult.success("BOC exchange rate fetch finished", result);
    }
}
