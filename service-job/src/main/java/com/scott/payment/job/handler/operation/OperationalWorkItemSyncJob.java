package com.scott.payment.job.handler.operation;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.admin.AdminInternalClient;
import com.scott.payment.job.client.admin.dto.OperationalWorkItemSyncClientDTOs.Request;
import com.scott.payment.job.client.admin.dto.OperationalWorkItemSyncClientDTOs.Response;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Clock;
import java.time.LocalDateTime;

/** 调用 service-admin 同步运营待办；任务服务不读取或修改任何来源业务状态。 */
@Component
public class OperationalWorkItemSyncJob implements JobHandler {

    /** 系统任务编码。 */
    public static final String JOB_CODE = "OPERATIONAL_WORK_ITEM_SYNC";

    /** 调度处理器编码。 */
    public static final String HANDLER_CODE = "operationalWorkItemSync";

    private final AdminInternalClient adminInternalClient;
    private final Clock clock;

    /** 创建使用系统时钟的统一待办同步任务。 */
    @Autowired
    public OperationalWorkItemSyncJob(AdminInternalClient adminInternalClient) {
        this(adminInternalClient, Clock.systemDefaultZone());
    }

    /** 创建可注入时钟的处理器，保证时间边界测试可重复。 */
    OperationalWorkItemSyncJob(AdminInternalClient adminInternalClient, Clock clock) {
        this.adminInternalClient = adminInternalClient;
        this.clock = clock;
    }

    /** 声明禁止同一任务并发执行的同步处理器。 */
    @Override
    public JobHandlerDescriptor descriptor() {
        return JobHandlerDescriptor.sync(
                HANDLER_CODE,
                "统一运营待办同步",
                "operations",
                "调用管理服务幂等聚合退款审批、结算复核、汇率预警和商户通知失败待办");
    }

    /** 使用 runId 构造稳定请求键，任务重试不会重复创建待办或轨迹。 */
    @Override
    public JobExecuteResult execute(JobExecuteContext context) {
        if (context == null || !StringUtils.hasText(context.getRunId())) {
            throw new ServiceException(ApiResultEnum.PARAM_MISSING.getCode(),
                    "job runId is required");
        }
        Request request = new Request();
        request.setRequestKey(JOB_CODE + ":" + context.getRunId().trim());
        request.setSyncTime(LocalDateTime.now(clock));
        Response response = adminInternalClient.synchronizeOperationalWorkItems(request);
        if (response == null) {
            throw new ServiceException(ApiResultEnum.BAD_GATEWAY.getCode(),
                    "service-admin work item sync result is empty");
        }
        String message = "operational work item sync finished, scanned="
                + value(response.getScannedSourceCount())
                + ", created=" + value(response.getCreatedCount())
                + ", reopened=" + value(response.getReopenedCount())
                + ", closed=" + value(response.getClosedCount());
        return JobExecuteResult.success(message, response);
    }

    private int value(Integer count) {
        return count == null ? 0 : count;
    }
}
