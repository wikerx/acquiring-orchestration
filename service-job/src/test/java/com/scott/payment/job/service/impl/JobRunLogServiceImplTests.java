package com.scott.payment.job.service.impl;

import com.scott.payment.component.job.enums.JobRunStatusEnum;
import com.scott.payment.job.mapper.SysJobRunLogMapper;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunLogServiceImplTests
 * @date : 2026-08-03 00:00
 * @email : scott_x@163.com
 * @description : 验证任务运行日志的开始、结束时间使用同一应用时钟和 JDBC 参数时区语义。
 * @status : create
 */
class JobRunLogServiceImplTests {

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
}
