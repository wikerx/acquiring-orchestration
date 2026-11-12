package com.scott.payment.job.application;

import com.scott.payment.job.api.internal.dto.JobExecutionTrendResponse;
import com.scott.payment.job.converter.JobSchedulerConverter;
import com.scott.payment.job.service.JobRunLogService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证任务执行趋势使用固定七个自然日，并对没有终态日志的日期补零。
 */
class JobRunLogApplicationServiceTest {

    /**
     * 验证 Spring 在存在测试专用构造器时仍能确定生产依赖注入入口。
     */
    @Test
    void shouldResolveProductionConstructorInSpringContext() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(JobRunLogService.class, () -> mock(JobRunLogService.class));
            context.registerBean(JobSchedulerConverter.class, () -> mock(JobSchedulerConverter.class));
            context.register(JobRunLogApplicationService.class);

            context.refresh();

            assertThat(context.getBean(JobRunLogApplicationService.class)).isNotNull();
        }
    }

    @Test
    void shouldReturnSevenNaturalDaysAndFillMissingDaysWithZero() {
        JobRunLogService service = mock(JobRunLogService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-11-11T04:00:00Z"), ZoneId.of("Asia/Shanghai"));
        JobRunLogApplicationService applicationService = new JobRunLogApplicationService(
                service,
                mock(JobSchedulerConverter.class),
                clock
        );
        JobExecutionTrendResponse.DailyPoint source = new JobExecutionTrendResponse.DailyPoint();
        source.setDate(LocalDate.of(2026, 11, 9));
        source.setSuccessCount(8L);
        source.setFailedCount(2L);
        when(service.summarizeExecutionTrend(
                LocalDateTime.of(2026, 11, 5, 0, 0),
                LocalDateTime.of(2026, 11, 12, 0, 0)))
                .thenReturn(List.of(source));

        JobExecutionTrendResponse response = applicationService.executionTrend();

        assertThat(response.getStartDate()).isEqualTo(LocalDate.of(2026, 11, 5));
        assertThat(response.getEndDate()).isEqualTo(LocalDate.of(2026, 11, 11));
        assertThat(response.getPoints()).hasSize(7);
        assertThat(response.getPoints()).extracting(JobExecutionTrendResponse.DailyPoint::getDate)
                .containsExactly(
                        LocalDate.of(2026, 11, 5),
                        LocalDate.of(2026, 11, 6),
                        LocalDate.of(2026, 11, 7),
                        LocalDate.of(2026, 11, 8),
                        LocalDate.of(2026, 11, 9),
                        LocalDate.of(2026, 11, 10),
                        LocalDate.of(2026, 11, 11));
        assertThat(response.getPoints().get(0).getSuccessCount()).isZero();
        assertThat(response.getPoints().get(4).getSuccessCount()).isEqualTo(8L);
        assertThat(response.getPoints().get(4).getFailedCount()).isEqualTo(2L);
        verify(service).summarizeExecutionTrend(
                LocalDateTime.of(2026, 11, 5, 0, 0),
                LocalDateTime.of(2026, 11, 12, 0, 0));
    }
}
