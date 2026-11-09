package com.scott.payment.job.handler.export;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.data.DataInternalClient;
import com.scott.payment.job.client.data.dto.DataExportTaskDispatchClientDTOs.Request;
import com.scott.payment.job.client.data.dto.DataExportTaskDispatchClientDTOs.Response;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 异步导出任务调度处理器测试，验证稳定请求键和失败边界。
 */
class ExportTaskDispatchJobTest {

    /**
     * 同一运行记录必须形成稳定请求键，并透传 Data 返回的真实统计。
     */
    @Test
    void shouldDispatchWithStableRequestId() {
        DataInternalClient client = mock(DataInternalClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-09T02:15:00Z"), ZoneId.of("Asia/Shanghai"));
        ExportTaskDispatchJob job = new ExportTaskDispatchJob(client, clock);
        Response response = new Response();
        response.setClaimedCount(1);
        response.setSuccessCount(1);
        response.setFailedCount(0);
        response.setExpiredCount(2);
        when(client.dispatchExportTasks(any())).thenReturn(response);
        JobExecuteContext context = new JobExecuteContext();
        context.setRunId("run-001");

        JobExecuteResult result = job.execute(context);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(client).dispatchExportTasks(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getRequestId())
                .isEqualTo("DATA_EXPORT_TASK_DISPATCH:run-001");
        assertThat(requestCaptor.getValue().getDispatchTime())
                .isEqualTo(LocalDateTime.of(2026, 11, 9, 10, 15));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("claimed=1", "success=1", "failed=0", "expired=2");
        assertThat(result.getData()).isSameAs(response);
    }

    /**
     * 缺少运行编号时不得创建无法审计和去重的调度命令。
     */
    @Test
    void shouldRejectMissingRunId() {
        ExportTaskDispatchJob job = new ExportTaskDispatchJob(
                mock(DataInternalClient.class), Clock.systemUTC());

        assertThatThrownBy(() -> job.execute(new JobExecuteContext()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("runId");
    }

    /**
     * Data 未返回执行结果时任务必须失败，不能把仅发出请求误记为成功。
     */
    @Test
    void shouldRejectEmptyDataResult() {
        DataInternalClient client = mock(DataInternalClient.class);
        when(client.dispatchExportTasks(any())).thenReturn(null);
        ExportTaskDispatchJob job = new ExportTaskDispatchJob(client, Clock.systemUTC());
        JobExecuteContext context = new JobExecuteContext();
        context.setRunId("run-002");

        assertThatThrownBy(() -> job.execute(context))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("result is empty");
    }
}
