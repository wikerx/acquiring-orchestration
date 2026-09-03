package com.scott.payment.job.executor;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.enums.JobRunStatusEnum;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.component.job.executor.JobHandler;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.component.job.model.JobExecuteResult;
import com.scott.payment.job.api.internal.dto.JobManualTriggerRequest;
import com.scott.payment.job.entity.SysJobRunLogDO;
import com.scott.payment.job.entity.SysJobTaskDO;
import com.scott.payment.job.service.JobRunLogService;
import com.scott.payment.job.service.JobTaskService;
import com.scott.payment.job.service.JobTaskTimingService;
import com.scott.payment.job.support.JobNodeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobDispatchServiceTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 调度分发锁和手动非阻塞执行测试。
 * @status : create
 */
class JobDispatchServiceTests {

    /** Executes manually triggered jobs without blocking the caller. */
    private ThreadPoolTaskExecutor taskExecutor;

    /** Schedules delayed retries used by the dispatch fixture. */
    private ThreadPoolTaskScheduler delayScheduler;

    @AfterEach
    void shutdownExecutors() {
        if (taskExecutor != null) {
            taskExecutor.shutdown();
        }
        if (delayScheduler != null) {
            delayScheduler.shutdown();
        }
    }

    @Test
    void manualTriggerShouldRejectWhenTaskIsAlreadyRunning() {
        Fixture fixture = fixture(successfulHandler());
        when(fixture.jobTaskService().tryAcquireLock(eq(fixture.task()), eq("job-node"), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> fixture.service().triggerManual(14L, new JobManualTriggerRequest()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("job task is already running");

        verifyNoInteractions(fixture.jobRunLogService());
    }

    @Test
    void manualTriggerShouldRejectDisabledTaskBeforeAcquiringLock() {
        Fixture fixture = fixture(successfulHandler());
        fixture.task().setStatus("DISABLED");

        assertThatThrownBy(() -> fixture.service().triggerManual(14L, new JobManualTriggerRequest()))
                .isInstanceOf(ServiceException.class)
                .hasMessage("job task is disabled");

        verify(fixture.jobTaskService(), never()).tryAcquireLock(any(), any(), any());
        verifyNoInteractions(fixture.jobRunLogService());
    }

    @Test
    void manualTriggerShouldReturnRunIdBeforeSyncHandlerCompletes() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        JobHandler handler = new JobHandler() {
            @Override
            public JobExecuteResult execute(JobExecuteContext context) {
                started.countDown();
                try {
                    if (!release.await(3, TimeUnit.SECONDS)) {
                        return JobExecuteResult.failed("F500", "test handler timeout");
                    }
                    return JobExecuteResult.success("done");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return JobExecuteResult.failed("F500", "test handler interrupted");
                }
            }

            @Override
            public JobHandlerDescriptor descriptor() {
                return JobHandlerDescriptor.sync("merchantNotificationRetry", "retry", "transaction", "retry");
            }
        };
        Fixture fixture = fixture(handler);
        when(fixture.jobTaskService().tryAcquireLock(eq(fixture.task()), eq("job-node"), any()))
                .thenReturn(true);

        long startNanos = System.nanoTime();
        String runId = fixture.service().triggerManual(14L, new JobManualTriggerRequest());
        long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);

        assertThat(runId).isNotBlank();
        assertThat(elapsedMillis).isLessThan(1_000L);
        assertThat(started.await(1, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        verify(fixture.jobRunLogService(), timeout(2_000)).finishAsSuccess(eq(99L), any(Long.class), eq("done"));
        verify(fixture.jobTaskService(), timeout(2_000))
                .finishTaskRun(eq(14L), any(), eq("job-node"));
    }

    @Test
    void rejectedRetryScheduleShouldReleaseTaskLock() {
        Fixture fixture = fixture(failingHandler());
        fixture.task().setRetryCount(1);
        delayScheduler.shutdown();
        when(fixture.jobTaskService().tryAcquireLock(eq(fixture.task()), eq("job-node"), any()))
                .thenReturn(true);

        String runId = fixture.service().triggerManual(14L, new JobManualTriggerRequest());

        assertThat(runId).isNotBlank();
        verify(fixture.jobRunLogService(), timeout(2_000))
                .finishAsFailed(eq(99L), any(Long.class), eq("failed"));
        verify(fixture.jobTaskService(), timeout(2_000))
                .finishTaskRun(eq(14L), eq(JobRunStatusEnum.FAILED), eq("job-node"));
    }

    /** 重试调度异常可能携带任务参数，结构化日志不得附加完整 Throwable。 */
    @Test
    void rejectedRetryScheduleShouldLogExceptionTypeWithoutThrowable() {
        Fixture fixture = fixture(failingHandler());
        fixture.task().setRetryCount(1);
        delayScheduler.shutdown();
        when(fixture.jobTaskService().tryAcquireLock(eq(fixture.task()), eq("job-node"), any()))
                .thenReturn(true);

        Logger logger = (Logger) LoggerFactory.getLogger(JobDispatchService.class);
        boolean additive = logger.isAdditive();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setAdditive(false);
        logger.addAppender(appender);
        try {
            fixture.service().triggerManual(14L, new JobManualTriggerRequest());
            verify(fixture.jobTaskService(), timeout(2_000))
                    .finishTaskRun(eq(14L), eq(JobRunStatusEnum.FAILED), eq("job-node"));

            ILoggingEvent failureEvent = appender.list.stream()
                    .filter(event -> event.getFormattedMessage().contains("JOB_RETRY_SCHEDULE_FAILED"))
                    .findFirst()
                    .orElseThrow();
            assertThat(failureEvent.getThrowableProxy()).isNull();
            assertThat(failureEvent.getFormattedMessage()).contains("exceptionType:");
        } finally {
            logger.detachAppender(appender);
            logger.setAdditive(additive);
            appender.stop();
        }
    }

    private Fixture fixture(JobHandler handler) {
        JobTaskService taskService = mock(JobTaskService.class);
        JobRunLogService runLogService = mock(JobRunLogService.class);
        JobTaskTimingService timingService = mock(JobTaskTimingService.class);
        JobNodeContext nodeContext = mock(JobNodeContext.class);
        when(nodeContext.nodeId()).thenReturn("job-node");

        SysJobTaskDO task = task();
        when(taskService.getRequiredTask(14L)).thenReturn(task);
        SysJobRunLogDO runLog = new SysJobRunLogDO();
        runLog.setId(99L);
        when(runLogService.createWaitingLog(eq(task), any(), any())).thenReturn(runLog);

        taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(1);
        taskExecutor.setMaxPoolSize(1);
        taskExecutor.setQueueCapacity(10);
        taskExecutor.initialize();
        delayScheduler = new ThreadPoolTaskScheduler();
        delayScheduler.setPoolSize(1);
        delayScheduler.initialize();

        JobDispatchService service = new JobDispatchService(
                taskService,
                runLogService,
                timingService,
                new JobHandlerRegistry(List.of(handler)),
                new JobFutureRegistry(),
                taskExecutor,
                delayScheduler,
                nodeContext
        );
        return new Fixture(service, taskService, runLogService, handler, task);
    }

    private JobHandler successfulHandler() {
        return new JobHandler() {
            @Override
            public JobExecuteResult execute(JobExecuteContext context) {
                return JobExecuteResult.success("done");
            }

            @Override
            public JobHandlerDescriptor descriptor() {
                return JobHandlerDescriptor.sync("merchantNotificationRetry", "retry", "transaction", "retry");
            }
        };
    }

    private JobHandler failingHandler() {
        return new JobHandler() {
            @Override
            public JobExecuteResult execute(JobExecuteContext context) {
                return JobExecuteResult.failed("F500", "failed");
            }

            @Override
            public JobHandlerDescriptor descriptor() {
                return JobHandlerDescriptor.sync("merchantNotificationRetry", "retry", "transaction", "retry");
            }
        };
    }

    private SysJobTaskDO task() {
        SysJobTaskDO task = new SysJobTaskDO();
        task.setId(14L);
        task.setJobCode("MERCHANT_NOTIFICATION_RETRY");
        task.setJobName("merchant notification retry");
        task.setHandlerCode("merchantNotificationRetry");
        task.setSchedulerMode("DISTRIBUTED");
        task.setExecuteMode("SYNC");
        task.setTimeoutSeconds(300);
        task.setRetryCount(0);
        task.setRetryIntervalSeconds(60);
        task.setParams("{\"limit\":5}");
        task.setStatus("ENABLED");
        task.setVersion(0);
        return task;
    }

    private record Fixture(JobDispatchService service,
                           JobTaskService jobTaskService,
                           JobRunLogService jobRunLogService,
                           JobHandler handler,
                           SysJobTaskDO task) {
    }
}
