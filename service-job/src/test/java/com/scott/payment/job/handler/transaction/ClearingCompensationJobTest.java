package com.scott.payment.job.handler.transaction;

import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.client.clearing.ClearingInternalClient;
import com.scott.payment.job.client.clearing.dto.ClearingCompensationClientDTOs.Request;
import com.scott.payment.job.client.clearing.dto.ClearingCompensationClientDTOs.Response;
import com.scott.payment.job.dto.transaction.ClearingCompensationRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ClearingCompensationJobTest
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 验证清分补偿任务只负责编排远程游标分页、总量聚合和单次页数上限
 * @status : create
 */
class ClearingCompensationJobTest {

    @Test
    void springShouldSelectProductionConstructor() {
        ClearingInternalClient client = mock(ClearingInternalClient.class);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(ClearingInternalClient.class, () -> client);
            context.registerBean(ClearingCompensationJob.class);
            context.refresh();

            assertThat(context.getBean(ClearingCompensationJob.class)).isNotNull();
        }
    }

    @Test
    void executeShouldFollowCursorAndAggregateBoundedPages() {
        ClearingInternalClient client = mock(ClearingInternalClient.class);
        Clock clock = Clock.fixed(Instant.parse("2026-08-26T06:00:00Z"), ZoneOffset.UTC);
        ClearingCompensationJob job = new ClearingCompensationJob(client, clock);
        Response first = response(200, 10, 190, true, LocalDateTime.of(2026, 8, 26, 13, 50), 200L);
        Response second = response(25, 2, 23, false, null, null);
        when(client.scan(any())).thenReturn(first, second);

        ClearingCompensationRequest input = new ClearingCompensationRequest();
        input.setMode("SHADOW_WRITE");
        input.setBeginTime(LocalDateTime.of(2026, 8, 26, 13, 30));
        input.setEndTime(LocalDateTime.of(2026, 8, 26, 14, 0));
        input.setLimit(200);
        input.setMaxPages(2);
        JobExecuteContext context = new JobExecuteContext();
        context.setParamsJson(JsonUtils.toJsonString(input));

        JobExecuteResult result = job.execute(context);

        ArgumentCaptor<Request> captor = ArgumentCaptor.forClass(Request.class);
        verify(client, org.mockito.Mockito.times(2)).scan(captor.capture());
        List<Request> requests = captor.getAllValues();
        assertThat(requests.get(0).getCursorId()).isNull();
        assertThat(requests.get(1).getCursorId()).isEqualTo(200L);
        assertThat(requests.get(1).getCursorTransactionDateTime())
                .isEqualTo(LocalDateTime.of(2026, 8, 26, 13, 50));
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.getMode()).isEqualTo("SHADOW_WRITE");
            assertThat(request.getLimit()).isEqualTo(200);
        });
        assertThat(result.isSuccess()).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertThat(data)
                .containsEntry("pages", 2)
                .containsEntry("scanned", 225)
                .containsEntry("writes", 12)
                .containsEntry("skipped", 213)
                .containsEntry("truncated", false);
    }

    @Test
    void executeShouldStopAtConfiguredPageLimitAndReturnContinuationCursor() {
        ClearingInternalClient client = mock(ClearingInternalClient.class);
        ClearingCompensationJob job = new ClearingCompensationJob(
                client, Clock.fixed(Instant.parse("2026-08-26T06:00:00Z"), ZoneOffset.UTC));
        LocalDateTime cursorTime = LocalDateTime.of(2026, 8, 26, 13, 55);
        when(client.scan(any())).thenReturn(response(1000, 0, 1000, true, cursorTime, 1000L));
        ClearingCompensationRequest input = new ClearingCompensationRequest();
        input.setBeginTime(LocalDateTime.of(2026, 8, 26, 13, 30));
        input.setEndTime(LocalDateTime.of(2026, 8, 26, 14, 0));
        input.setMaxPages(1);
        input.setLimit(1000);
        JobExecuteContext context = new JobExecuteContext();
        context.setParamsJson(JsonUtils.toJsonString(input));

        JobExecuteResult result = job.execute(context);

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.getData();
        assertThat(data)
                .containsEntry("truncated", true)
                .containsEntry("nextCursorTransactionDateTime", cursorTime)
                .containsEntry("nextCursorId", 1000L);
    }

    private Response response(int scanned, int writes, int skipped, boolean hasMore,
                              LocalDateTime cursorTime, Long cursorId) {
        Response response = new Response();
        response.setScannedCount(scanned);
        response.setWriteCount(writes);
        response.setSkippedCount(skipped);
        response.setHasMore(hasMore);
        response.setNextCursorTransactionDateTime(cursorTime);
        response.setNextCursorId(cursorId);
        return response;
    }
}
