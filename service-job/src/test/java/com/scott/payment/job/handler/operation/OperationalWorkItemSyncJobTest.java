package com.scott.payment.job.handler.operation;

import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.admin.AdminInternalClient;
import com.scott.payment.job.client.admin.dto.OperationalWorkItemSyncClientDTOs.Request;
import com.scott.payment.job.client.admin.dto.OperationalWorkItemSyncClientDTOs.Response;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 统一运营待办同步处理器测试。 */
class OperationalWorkItemSyncJobTest {

    /** 同一 runId 必须产生稳定请求键，并透传管理服务同步统计。 */
    @Test
    void shouldSynchronizeWithStableRequestKey() {
        AdminInternalClient client = mock(AdminInternalClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-05T01:30:00Z"), ZoneId.of("Asia/Shanghai"));
        OperationalWorkItemSyncJob job = new OperationalWorkItemSyncJob(client, clock);
        Response response = new Response();
        response.setScannedSourceCount(12);
        response.setCreatedCount(4);
        response.setReopenedCount(1);
        response.setClosedCount(2);
        when(client.synchronizeOperationalWorkItems(org.mockito.ArgumentMatchers.any())).thenReturn(response);
        JobExecuteContext context = new JobExecuteContext();
        context.setRunId("run-001");

        JobExecuteResult result = job.execute(context);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(client).synchronizeOperationalWorkItems(requestCaptor.capture());
        assertThat(requestCaptor.getValue().getRequestKey())
                .isEqualTo("OPERATIONAL_WORK_ITEM_SYNC:run-001");
        assertThat(requestCaptor.getValue().getSyncTime())
                .isEqualTo(LocalDateTime.of(2026, 11, 5, 9, 30));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getMessage()).contains("scanned=12", "created=4", "reopened=1", "closed=2");
        assertThat(result.getData()).isSameAs(response);
    }

    /** 缺失 runId 时不得生成不可追踪的同步请求。 */
    @Test
    void shouldRejectMissingRunId() {
        OperationalWorkItemSyncJob job = new OperationalWorkItemSyncJob(
                mock(AdminInternalClient.class), Clock.systemUTC());

        assertThatThrownBy(() -> job.execute(new JobExecuteContext()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("runId");
    }
}
