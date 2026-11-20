package com.scott.payment.job.handler.operation;

import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.core.json.JsonUtils;
import com.alibaba.fastjson2.TypeReference;
import com.scott.payment.job.client.admin.AdminInternalClient;
import com.scott.payment.job.client.admin.dto.PaymentCapabilityOperationsClientDTOs.BoundaryRequest;
import com.scott.payment.job.client.admin.dto.PaymentCapabilityOperationsClientDTOs.BoundaryResponse;
import com.scott.payment.job.client.admin.dto.PaymentCapabilityOperationsClientDTOs.Cursor;
import com.scott.payment.job.entity.SysJobTaskDO;
import com.scott.payment.job.mapper.SysJobRunLogMapper;
import com.scott.payment.job.mapper.SysJobTaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentCapabilityBoundaryJobTests {

    private final LocalDateTime at = LocalDateTime.of(2026, 11, 20, 18, 0);
    private final AdminInternalClient admin = mock(AdminInternalClient.class);
    private final SysJobRunLogMapper runs = mock(SysJobRunLogMapper.class);
    private final SysJobTaskMapper tasks = mock(SysJobTaskMapper.class);
    private final PaymentCapabilityBoundaryJob job = new PaymentCapabilityBoundaryJob(admin, runs, tasks);

    @Test
    void rejectsAnExecutionWhoseEpochCannotBeProven() {
        assertThatThrownBy(() -> job.execute(context())).hasMessageContaining("lease was lost");
        verifyNoInteractions(admin, tasks);
    }

    @Test
    void continuesOtherSourcesWithoutAdvancingFailedSource() {
        configureLease();
        when(tasks.advanceFencedCheckpoint(eq(10L), eq("node"), eq(3L), anyLong(), anyString())).thenReturn(1);
        when(admin.scanPaymentCapabilityBoundaries(any())).thenAnswer(call -> {
            BoundaryRequest request = call.getArgument(0);
            if ("AUTH_EFFECTIVE".equals(request.source())) {
                throw new IllegalStateException("owner unavailable");
            }
            return new BoundaryResponse(null, false, 0);
        });

        assertThat(job.execute(context()).isSuccess()).isFalse();
        ArgumentCaptor<String> checkpoint = ArgumentCaptor.forClass(String.class);
        verify(tasks).advanceFencedCheckpoint(eq(10L), eq("node"), eq(3L), eq(1L), checkpoint.capture());
        Map<String, PaymentCapabilityBoundaryJob.Window> saved = JsonUtils.parseObject(checkpoint.getValue(), new TypeReference<>() { });
        assertThat(saved.get("AUTH_EFFECTIVE").after().time()).isEqualTo(at.minusMinutes(1));
        assertThat(saved.get("AUTH_EXPIRY").after().time()).isEqualTo(at);
    }

    @Test
    void cannotAdvanceCheckpointAfterTheOwnerCommitsButLeaseChanges() {
        configureLease();
        when(tasks.advanceFencedCheckpoint(eq(10L), eq("node"), eq(3L), eq(0L), anyString())).thenReturn(1);
        when(admin.scanPaymentCapabilityBoundaries(any())).thenReturn(
                new BoundaryResponse(new Cursor(at.minusSeconds(10), 13L), true, 1));

        assertThatThrownBy(() -> job.execute(context())).hasMessageContaining("checkpoint ownership changed");
        verify(tasks).advanceFencedCheckpoint(eq(10L), eq("node"), eq(3L), eq(1L), anyString());
    }

    private JobExecuteContext context() {
        JobExecuteContext context = new JobExecuteContext();
        context.setJobId(10L);
        context.setRunId("run");
        context.setExecutorNode("node");
        context.setActualTriggerTime(at);
        return context;
    }

    private void configureLease() {
        SysJobTaskDO task = new SysJobTaskDO();
        task.setCheckpointVersion(0L);
        when(runs.findRunningLease("run", 10L, "node")).thenReturn(3L);
        when(tasks.findFencedTask(10L, "node", 3L)).thenReturn(task);
    }
}
