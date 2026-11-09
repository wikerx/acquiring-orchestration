package com.scott.payment.job.handler.export;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.data.DataInternalClient;
import com.scott.payment.job.client.data.dto.DataExportTaskDispatchClientDTOs.Request;
import com.scott.payment.job.client.data.dto.DataExportTaskDispatchClientDTOs.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;

/**
 * 异步导出任务调度处理器，仅调用 Data 的幂等 owner command，不直接访问任务表或对象存储。
 */
@Component
public class ExportTaskDispatchJob implements JobHandler {
    public static final String JOB_CODE = "DATA_EXPORT_TASK_DISPATCH";
    public static final String HANDLER_CODE = "dataExportTaskDispatch";
    private final DataInternalClient dataInternalClient;
    private final Clock clock;

    /**
     * 创建使用系统时钟的导出任务调度处理器。
     */
    @Autowired
    public ExportTaskDispatchJob(DataInternalClient dataInternalClient) {
        this(dataInternalClient, Clock.systemDefaultZone());
    }

    /**
     * 创建可注入时钟的处理器，保证调度边界测试可重复。
     */
    ExportTaskDispatchJob(DataInternalClient dataInternalClient, Clock clock) {
        this.dataInternalClient = dataInternalClient;
        this.clock = clock;
    }

    /**
     * 声明同步且禁止同一任务并发执行的 Data 导出调度处理器。
     */
    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(
                HANDLER_CODE,
                "异步导出任务调度",
                "data",
                "调用数据服务有界领取导出任务并清理到期私有文件");
    }

    /**
     * 使用 runId 构造稳定请求键，重复触发仍由 Data 租约和版本 CAS 保护。
     */
    @Override
    public JobExecuteResult execute(JobExecuteContext context) {
        if (context == null || !StringUtils.hasText(context.getRunId())) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(), "job runId is required");
        }
        Request request = new Request();
        request.setRequestId(JOB_CODE + ":" + context.getRunId().trim());
        request.setDispatchTime(LocalDateTime.now(clock));
        Response response = dataInternalClient.dispatchExportTasks(request);
        if (response == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-data export dispatch result is empty");
        }
        String message = "export task dispatch finished, claimed=" + value(response.getClaimedCount())
                + ", success=" + value(response.getSuccessCount())
                + ", failed=" + value(response.getFailedCount())
                + ", expired=" + value(response.getExpiredCount());
        return JobExecuteResult.success(message, response);
    }

    /**
     * 将空统计值规范为零，保持运行日志摘要稳定。
     */
    private int value(Integer count) {
        return count == null ? 0 : count;
    }
}
