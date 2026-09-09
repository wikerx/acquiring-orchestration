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
 * @date : 2026-07-03 19:00
 * @email : scott_x@163.com
 * @description : Boc Exchange Rate Fetch Job 任务组件，位于 调度任务服务，执行定时扫描、分片调度、补偿处理或后台同步，并记录任务执行结果。
 * @status : create
 */
@Component
public class BocExchangeRateFetchJob implements JobHandler {

    /**
     * {@code HANDLER_CODE}，用于在系统、渠道、字典或配置中稳定引用当前业务取值。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String HANDLER_CODE = "bocExchangeRateFetchJob";

    /**
     * {@code exchangeRateFetchService} 依赖，用于 {@code BocExchangeRateFetchJob} 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：比例值；格式：decimal，按费率或汇率精度保存；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围由费率、汇率或预警配置定义；数据来源：Spring 容器构造器注入。
     * </p>
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
