package com.scott.payment.job.handler.exchange;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.ExchangeRateFetchRequest;
import com.scott.payment.job.dto.exchange.ExchangeRateFetchDTOs.ExchangeRateFetchResult;
import com.scott.payment.job.exchange.provider.NbpExchangeRateProvider;
import com.scott.payment.job.exchange.service.ExchangeRateFetchService;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : NbpExchangeRateFetchJob
 * @date : 2026-08-08 00:00
 * @email : scott_x@163.com
 * @description : NBP 汇率拉取任务处理器，固定拉取 Table C 外币兑 PLN 报价并复用统一原始汇率和业务汇率生成链路。
 * @status : create
 */
@Component
public class NbpExchangeRateFetchJob implements JobHandler {

    /** 调度中心注册的 NBP 任务处理器编码。 */
    public static final String HANDLER_CODE = "nbpExchangeRateFetchJob";

    private final ExchangeRateFetchService exchangeRateFetchService;

    /**
     * 创建 NBP 汇率拉取任务处理器。
     *
     * @param exchangeRateFetchService 汇率源拉取服务
     */
    public NbpExchangeRateFetchJob(ExchangeRateFetchService exchangeRateFetchService) {
        this.exchangeRateFetchService = exchangeRateFetchService;
    }

    /**
     * 声明 NBP 任务处理器元数据。
     *
     * @return NBP 同步任务描述
     */
    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(
                HANDLER_CODE,
                "波兰国家银行汇率拉取任务",
                "exchange",
                "定时拉取 NBP Table C 外币兑 PLN 买卖报价并写入原始汇率记录"
        );
    }

    /**
     * 执行 NBP 汇率拉取任务，强制使用 NBP 来源编码。
     *
     * @param context 任务执行上下文，包含运行 ID 和 dryRun 等任务参数
     * @return 调度中心可识别的执行结果
     */
    @Override
    public JobExecuteResult execute(JobExecuteContext context) {
        if (context == null) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "job execute context is required");
        }
        ExchangeRateFetchRequest request = context.parseParams(ExchangeRateFetchRequest.class);
        if (request == null) {
            request = new ExchangeRateFetchRequest();
        }
        request.setSourceCode(NbpExchangeRateProvider.SOURCE_CODE);
        ExchangeRateFetchResult result = exchangeRateFetchService.fetch(request, context);
        if ("FAILED".equals(result.getFetchStatus())) {
            return JobExecuteResult.failed(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), result.getErrorMessage());
        }
        return JobExecuteResult.success("NBP exchange rate fetch finished", result);
    }
}
