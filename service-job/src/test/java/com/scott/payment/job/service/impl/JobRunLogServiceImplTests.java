package com.scott.payment.job.service.impl;

import com.scott.payment.component.job.enums.JobRunStatusEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.job.api.internal.dto.JobRunLogQueryRequest;
import com.scott.payment.job.entity.SysJobRunLogDO;
import com.scott.payment.job.mapper.SysJobRunLogMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证任务运行日志的开始、结束时间使用同一应用时钟和 JDBC 参数时区语义。
 */
class JobRunLogServiceImplTests {

    @Test
    void legacyListShouldRejectOversizeBeforeReturningAnIncompleteExport() {
        SysJobRunLogMapper mapper = mock(SysJobRunLogMapper.class);
        JobRunLogServiceImpl service = new JobRunLogServiceImpl(mapper);
        when(mapper.selectList(any(Wrapper.class)))
                .thenReturn(Collections.nCopies(5_001, new SysJobRunLogDO()));

        assertThatThrownBy(() -> service.listLogs(new JobRunLogQueryRequest()))
                .isInstanceOf(ServiceException.class)
                .hasMessageContaining("异步导出");

        verify(mapper).selectList(any(Wrapper.class));
    }

    @Test
    void legacyListShouldKeepSmallExportCompatible() {
        SysJobRunLogMapper mapper = mock(SysJobRunLogMapper.class);
        SysJobRunLogDO log = new SysJobRunLogDO();
        when(mapper.selectList(any(Wrapper.class))).thenReturn(java.util.List.of(log));

        assertThat(new JobRunLogServiceImpl(mapper).listLogs(new JobRunLogQueryRequest()))
                .containsExactly(log);
    }

    @Test
    void finishShouldPassApplicationCompletionTimeToMapper() {
        SysJobRunLogMapper mapper = mock(SysJobRunLogMapper.class);
        JobRunLogServiceImpl service = new JobRunLogServiceImpl(mapper);
        ArgumentCaptor<LocalDateTime> endTimeCaptor = ArgumentCaptor.forClass(LocalDateTime.class);

        service.finishAsSuccess(7L, 25L, "done");

        verify(mapper).finishIfRunning(
                eq(7L),
                eq(JobRunStatusEnum.SUCCESS.name()),
                eq("done"),
                isNull(),
                eq(25L),
                endTimeCaptor.capture());
        assertThat(endTimeCaptor.getValue()).isNotNull();
    }

    @Test
    void mapperShouldNotMixDatabaseNowWithApplicationStartTime() throws NoSuchMethodException {
        Update update = SysJobRunLogMapper.class.getMethod(
                "finishIfRunning",
                Long.class,
                String.class,
                String.class,
                String.class,
                Long.class,
                LocalDateTime.class).getAnnotation(Update.class);
        String sql = String.join(" ", update.value());

        assertThat(sql).contains("end_time = GREATEST(#{endTime}, start_time)");
        assertThat(sql).contains("update_time = GREATEST(#{endTime}, start_time)");
        assertThat(sql).doesNotContain("NOW()");
    }

    @Test
    void trendQueryShouldCountOnlyTerminalStatusesInHalfOpenRange() throws NoSuchMethodException {
        Select select = SysJobRunLogMapper.class.getMethod(
                "selectDailyTrend", LocalDateTime.class, LocalDateTime.class).getAnnotation(Select.class);
        String sql = String.join(" ", select.value());

        assertThat(sql).contains("COALESCE(end_time, update_time) >= #{startTime}");
        assertThat(sql).contains("COALESCE(end_time, update_time) < #{endTime}");
        assertThat(sql).contains("run_status = 'SUCCESS'");
        assertThat(sql).contains("'FAILED', 'TIMEOUT', 'CANCELLED'");
        assertThat(sql).doesNotContain("'WAITING'").doesNotContain("'RUNNING'");
    }
}
