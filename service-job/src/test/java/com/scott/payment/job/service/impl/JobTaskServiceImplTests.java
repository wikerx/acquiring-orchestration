package com.scott.payment.job.service.impl;

import com.scott.payment.component.job.enums.JobRunStatusEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.job.executor.JobHandlerRegistry;
import com.scott.payment.job.entity.SysJobTaskDO;
import com.scott.payment.job.mapper.SysJobTaskMapper;
import com.scott.payment.job.service.JobTaskTimingService;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 任务终态写入和持锁节点隔离测试。 */
class JobTaskServiceImplTests {

    @Test
    void disableTaskShouldPersistNullNextTriggerTimeWithoutTouchingLease() {
        SysJobTaskMapper mapper = mock(SysJobTaskMapper.class);
        JobTaskTimingService timingService = mock(JobTaskTimingService.class);
        SysJobTaskDO task = task(14L);
        when(mapper.selectById(14L)).thenReturn(task);
        when(mapper.updateStatus(eq(14L), eq("DISABLED"), eq(null), eq("operator"), any(LocalDateTime.class)))
                .thenReturn(1);
        JobTaskServiceImpl service = new JobTaskServiceImpl(
                mapper, timingService, mock(JobHandlerRegistry.class));

        SysJobTaskDO result = service.changeStatus(14L, "DISABLED", "operator");

        assertThat(result.getStatus()).isEqualTo("DISABLED");
        assertThat(result.getNextTriggerTime()).isNull();
        verify(mapper).updateStatus(
                eq(14L), eq("DISABLED"), eq(null), eq("operator"), any(LocalDateTime.class));
    }

    @Test
    void changeStatusShouldRejectMissingDatabaseRow() {
        SysJobTaskMapper mapper = mock(SysJobTaskMapper.class);
        SysJobTaskDO task = task(14L);
        when(mapper.selectById(14L)).thenReturn(task);
        JobTaskServiceImpl service = new JobTaskServiceImpl(
                mapper, mock(JobTaskTimingService.class), mock(JobHandlerRegistry.class));

        assertThatThrownBy(() -> service.changeStatus(14L, "DISABLED", "operator"))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("status update failed");
    }

    @Test
    void finishTaskRunShouldReleaseOnlyCurrentOwnerLock() {
        SysJobTaskMapper mapper = mock(SysJobTaskMapper.class);
        when(mapper.finishTaskRun(
                org.mockito.ArgumentMatchers.eq(14L),
                org.mockito.ArgumentMatchers.eq("job-node"),
                org.mockito.ArgumentMatchers.eq("SUCCESS"),
                any(LocalDateTime.class)))
                .thenReturn(1);
        JobTaskServiceImpl service = new JobTaskServiceImpl(
                mapper,
                mock(JobTaskTimingService.class),
                mock(JobHandlerRegistry.class));

        assertThat(service.finishTaskRun(14L, JobRunStatusEnum.SUCCESS, "job-node"))
                .isTrue();
        verify(mapper).finishTaskRun(
                org.mockito.ArgumentMatchers.eq(14L),
                org.mockito.ArgumentMatchers.eq("job-node"),
                org.mockito.ArgumentMatchers.eq("SUCCESS"),
                any(LocalDateTime.class));
    }

    @Test
    void finishTaskRunShouldReportLockOwnerMismatch() {
        SysJobTaskMapper mapper = mock(SysJobTaskMapper.class);
        JobTaskServiceImpl service = new JobTaskServiceImpl(
                mapper,
                mock(JobTaskTimingService.class),
                mock(JobHandlerRegistry.class));

        assertThat(service.finishTaskRun(14L, JobRunStatusEnum.FAILED, "stale-node"))
                .isFalse();
    }

    @Test
    void finishTaskRunSqlShouldClearLeaseWithOwnerCondition() throws NoSuchMethodException {
        Update update = SysJobTaskMapper.class
                .getMethod("finishTaskRun", Long.class, String.class, String.class, LocalDateTime.class)
                .getAnnotation(Update.class);
        String sql = String.join(" ", update.value());

        assertThat(sql).contains("lock_owner = NULL");
        assertThat(sql).contains("lock_until = NULL");
        assertThat(sql).contains("AND lock_owner = #{nodeId}");
    }

    @Test
    void updateStatusSqlShouldClearNextTriggerWithoutUpdatingLeaseColumns() throws NoSuchMethodException {
        Update update = SysJobTaskMapper.class
                .getMethod("updateStatus", Long.class, String.class, LocalDateTime.class,
                        String.class, LocalDateTime.class)
                .getAnnotation(Update.class);
        String sql = String.join(" ", update.value());

        assertThat(sql).contains("next_trigger_time = #{nextTriggerTime}");
        assertThat(sql).doesNotContain("lock_owner");
        assertThat(sql).doesNotContain("lock_until");
        assertThat(sql).doesNotContain("version =");
    }

    private SysJobTaskDO task(Long id) {
        SysJobTaskDO task = new SysJobTaskDO();
        task.setId(id);
        task.setDeleted(0);
        task.setStatus("ENABLED");
        task.setNextTriggerTime(LocalDateTime.of(2026, 8, 3, 5, 0));
        task.setVersion(7);
        task.setLockOwner("job-node");
        task.setLockUntil(LocalDateTime.of(2026, 8, 3, 5, 5));
        return task;
    }
}
