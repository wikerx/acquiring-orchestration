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
 * 中国银行汇率拉取任务处理器。
 *
 * <p>处理器只负责调度入口和结果转换，汇率源拉取、解析、去重和日志记录由汇率拉取服务完成。</p>
 */
@Component
public class BocExchangeRateFetchJob implements JobHandler {

    public static final String HANDLER_CODE = "bocExchangeRateFetchJob";

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
