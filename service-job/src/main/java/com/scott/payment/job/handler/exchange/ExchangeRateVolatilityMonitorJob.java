package com.scott.payment.job.handler.exchange;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.admin.AdminInternalClient;
import com.scott.payment.job.client.admin.dto.ExchangeRateAlertEvaluationClientDTOs.Request;
import com.scott.payment.job.client.admin.dto.ExchangeRateAlertEvaluationClientDTOs.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 调用管理服务执行汇率波动评估；本处理器不读取汇率表，也不写预警业务表。
 */
@Component
public class ExchangeRateVolatilityMonitorJob implements JobHandler {

    /** 系统任务编码。 */
    public static final String JOB_CODE = "EXCHANGE_RATE_VOLATILITY_MONITOR";

    /** 调度处理器编码。 */
    public static final String HANDLER_CODE = "exchangeRateVolatilityMonitorJob";

    private final AdminInternalClient adminInternalClient;
    private final Clock clock;

    /**
     * 创建使用系统时钟的汇率波动监控任务。
     *
     * @param adminInternalClient 管理服务内部客户端
     */
    @Autowired
    public ExchangeRateVolatilityMonitorJob(AdminInternalClient adminInternalClient) {
        this(adminInternalClient, Clock.systemDefaultZone());
    }

    /**
     * 创建可注入时钟的任务处理器，供时间边界测试稳定复现。
     */
    ExchangeRateVolatilityMonitorJob(AdminInternalClient adminInternalClient, Clock clock) {
        this.adminInternalClient = adminInternalClient;
        this.clock = clock;
    }

    /**
     * 声明同步、禁止并发的汇率波动监控处理器。
     *
     * @return 调度中心处理器描述
     */
    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(
                HANDLER_CODE,
                "汇率波动监控",
                "exchange",
                "调用管理服务按交易汇率和结算汇率分别评估跨来源汇率波动");
    }

    /**
     * 使用 runId 生成稳定请求键并显式传递本轮评估时间。
     *
     * @param context 调度执行上下文
     * @return 管理服务返回的真实评估统计
     */
    @Override
    public JobExecuteResult execute(JobExecuteContext context) {
        if (context == null || !StringUtils.hasText(context.getRunId())) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(),
                    "job runId is required");
        }
        Request request = new Request();
        request.setRequestKey(JOB_CODE + ":" + context.getRunId().trim());
        request.setEvaluationTime(LocalDateTime.now(clock));
        Response response = adminInternalClient.evaluateExchangeRateAlerts(request);
        if (response == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-admin evaluation result is empty");
        }
        String message = "exchange rate volatility evaluation finished, rules="
                + value(response.getEvaluatedRuleCount())
                + ", pairs=" + value(response.getEvaluatedPairCount())
                + ", triggered=" + value(response.getTriggeredEventCount())
                + ", resolved=" + value(response.getResolvedEventCount());
        return JobExecuteResult.success(message, response);
    }

    /**
     * 将空统计值规范为零，保证任务摘要稳定可读。
     */
    private int value(Integer count) {
        return count == null ? 0 : count;
    }
}
