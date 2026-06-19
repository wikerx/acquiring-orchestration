package com.scott.payment.job.executor;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.job.enums.JobExecuteModeEnum;
import com.scott.payment.component.job.enums.JobRunStatusEnum;
import com.scott.payment.component.job.enums.JobSchedulerModeEnum;
import com.scott.payment.component.job.enums.JobTriggerTypeEnum;
import com.scott.payment.component.job.executor.AsyncJobHandler;
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
import com.scott.payment.job.support.JobParameterMasker;
import com.scott.payment.job.support.TraceIdSupport;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobDispatchService
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 调度中心任务分发编排服务
 * @status : create
 */

@Service
public class JobDispatchService {

    private static final int LOCK_BUFFER_SECONDS = 30;

    private final JobTaskService jobTaskService;
    private final JobRunLogService jobRunLogService;
    private final JobTaskTimingService jobTaskTimingService;
    private final JobHandlerRegistry jobHandlerRegistry;
    private final JobFutureRegistry jobFutureRegistry;
    private final ThreadPoolTaskExecutor jobTaskExecutor;
    private final ThreadPoolTaskScheduler jobDelayTaskScheduler;
    private final JobNodeContext jobNodeContext;

    /**
     * 创建任务分发服务。
     *
     * @param jobTaskService      任务领域服务
     * @param jobRunLogService    执行日志领域服务
     * @param jobTaskTimingService 调度时间计算服务
     * @param jobHandlerRegistry  处理器注册中心
     * @param jobFutureRegistry   运行中任务 Future 注册表
     * @param jobTaskExecutor     任务执行线程池
     * @param jobDelayTaskScheduler 延迟调度线程池
     * @param jobNodeContext      当前节点上下文
     */
    public JobDispatchService(JobTaskService jobTaskService,
                              JobRunLogService jobRunLogService,
                              JobTaskTimingService jobTaskTimingService,
                              JobHandlerRegistry jobHandlerRegistry,
                              JobFutureRegistry jobFutureRegistry,
                              ThreadPoolTaskExecutor jobTaskExecutor,
                              ThreadPoolTaskScheduler jobDelayTaskScheduler,
                              JobNodeContext jobNodeContext) {
        this.jobTaskService = jobTaskService;
        this.jobRunLogService = jobRunLogService;
        this.jobTaskTimingService = jobTaskTimingService;
        this.jobHandlerRegistry = jobHandlerRegistry;
        this.jobFutureRegistry = jobFutureRegistry;
        this.jobTaskExecutor = jobTaskExecutor;
        this.jobDelayTaskScheduler = jobDelayTaskScheduler;
        this.jobNodeContext = jobNodeContext;
    }

    /**
     * 触发定时任务执行。
     *
     * @param task 到期任务
     */
    public void triggerScheduled(SysJobTaskDO task) {
        dispatch(task, JobTriggerTypeEnum.SCHEDULE, task.getParams(), null, null, 0);
    }

    /**
     * 触发手动执行。
     *
     * @param taskId  任务主键
     * @param request 手动执行请求
     * @return 首次执行生成的 runId
     */
    public String triggerManual(Long taskId, JobManualTriggerRequest request) {
        SysJobTaskDO task = jobTaskService.getRequiredTask(taskId);
        String paramsJson = request.getParamsJson() == null || request.getParamsJson().isBlank()
                ? task.getParams()
                : request.getParamsJson();
        return dispatch(task, JobTriggerTypeEnum.MANUAL, paramsJson, request.getOperatorId(), request.getOperatorName(), 0);
    }

    /**
     * 标记超时执行。
     *
     * @param runLog 超时执行日志
     */
    public void markTimeout(SysJobRunLogDO runLog) {
        jobFutureRegistry.cancel(runLog.getRunId());
        boolean updated = jobRunLogService.finishAsTimeout(runLog);
        if (updated) {
            jobTaskService.finishTaskRun(runLog.getJobId(), JobRunStatusEnum.TIMEOUT);
        }
    }

    /**
     * 统一分发任务执行。
     *
     * @param task         任务定义
     * @param triggerType  触发类型
     * @param paramsJson   参数 JSON
     * @param operatorId   操作人 ID
     * @param operatorName 操作人名称
     * @param retryIndex   当前重试序号
     * @return 执行批次号
     */
    private String dispatch(SysJobTaskDO task,
                            JobTriggerTypeEnum triggerType,
                            String paramsJson,
                            String operatorId,
                            String operatorName,
                            int retryIndex) {
        JobHandlerDescriptor descriptor = jobHandlerRegistry.getRequiredDescriptor(task.getHandlerCode());
        if (triggerType == JobTriggerTypeEnum.MANUAL && Boolean.FALSE.equals(descriptor.getAllowManualTrigger())) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "job handler does not allow manual trigger");
        }
        JobExecuteContext context = buildContext(task, triggerType, paramsJson, operatorId, operatorName, retryIndex);
        LocalDateTime triggerTime = context.getActualTriggerTime();
        jobTaskService.extendLock(task.getId(), jobNodeContext.nodeId(), calculateLockUntil(task, triggerTime));
        if (triggerType == JobTriggerTypeEnum.SCHEDULE) {
            jobTaskService.markScheduled(task.getId(), triggerTime, jobTaskTimingService.calculateNextTriggerTime(task, triggerTime));
        }
        SysJobRunLogDO runLog = jobRunLogService.createWaitingLog(task, context, JobParameterMasker.mask(paramsJson));
        jobRunLogService.markRunning(runLog.getId());
        JobHandler handler = jobHandlerRegistry.getRequiredHandler(task.getHandlerCode());
        AsyncJobHandler asyncJobHandler = handler instanceof AsyncJobHandler
                ? (AsyncJobHandler) handler
                : null;
        if (asyncJobHandler != null || descriptor.getExecuteMode() == JobExecuteModeEnum.ASYNC) {
            executeAsync(task, context, runLog, asyncJobHandler != null ? asyncJobHandler : castAsync(handler));
        } else {
            executeSync(task, context, runLog, handler);
        }
        return context.getRunId();
    }

    /**
     * 同步执行任务。
     *
     * @param task    任务定义
     * @param context 执行上下文
     * @param runLog  执行日志
     * @param handler 任务处理器
     */
    private void executeSync(SysJobTaskDO task,
                             JobExecuteContext context,
                             SysJobRunLogDO runLog,
                             JobHandler handler) {
        Instant start = Instant.now();
        CompletableFuture<JobExecuteResult> future = CompletableFuture.supplyAsync(() -> {
            TraceIdSupport.bindTraceId(context.getTraceId());
            try {
                return handler.execute(context);
            } finally {
                TraceIdSupport.clear();
            }
        }, jobTaskExecutor);
        jobFutureRegistry.register(context.getRunId(), future);
        try {
            JobExecuteResult result = future.get(task.getTimeoutSeconds(), TimeUnit.SECONDS);
            finishSuccessOrFailure(task, runLog, context, result, start);
        } catch (TimeoutException exception) {
            future.cancel(true);
            markTimeout(runLog);
        } catch (Exception exception) {
            JobExecuteResult result = JobExecuteResult.failed(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), exception.getMessage());
            finishSuccessOrFailure(task, runLog, context, result, start);
        } finally {
            jobFutureRegistry.unregister(context.getRunId());
        }
    }

    /**
     * 异步执行任务。
     *
     * @param task     任务定义
     * @param context  执行上下文
     * @param runLog   执行日志
     * @param handler  异步处理器
     */
    private void executeAsync(SysJobTaskDO task,
                              JobExecuteContext context,
                              SysJobRunLogDO runLog,
                              AsyncJobHandler handler) {
        Instant start = Instant.now();
        CompletableFuture<JobExecuteResult> future;
        try {
            future = handler.executeAsync(context);
        } catch (Exception exception) {
            future = CompletableFuture.failedFuture(exception);
        }
        jobFutureRegistry.register(context.getRunId(), future);
        future.whenComplete((result, throwable) -> {
            try {
                if (throwable != null) {
                    JobExecuteResult failedResult = JobExecuteResult.failed(
                            ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(),
                            throwable.getMessage()
                    );
                    finishSuccessOrFailure(task, runLog, context, failedResult, start);
                    return;
                }
                finishSuccessOrFailure(task, runLog, context, result, start);
            } finally {
                jobFutureRegistry.unregister(context.getRunId());
            }
        });
    }

    /**
     * 根据执行结果更新日志与任务终态，必要时触发失败重试。
     *
     * @param task    任务定义
     * @param runLog  执行日志
     * @param context 执行上下文
     * @param result  执行结果
     * @param start   开始时间
     */
    private void finishSuccessOrFailure(SysJobTaskDO task,
                                        SysJobRunLogDO runLog,
                                        JobExecuteContext context,
                                        JobExecuteResult result,
                                        Instant start) {
        long durationMs = Duration.between(start, Instant.now()).toMillis();
        if (result != null && result.isAccepted()) {
            return;
        }
        if (result != null && result.isSuccess()) {
            jobRunLogService.finishAsSuccess(runLog.getId(), durationMs, result.getMessage());
            jobTaskService.finishTaskRun(task.getId(), JobRunStatusEnum.SUCCESS);
            return;
        }
        String failureMessage = result == null ? "job execute result is null"
                : (result.getErrorMessage() == null || result.getErrorMessage().isBlank() ? result.getMessage() : result.getErrorMessage());
        jobRunLogService.finishAsFailed(runLog.getId(), durationMs, failureMessage);
        if (context.getRetryIndex() < task.getRetryCount()) {
            scheduleRetry(task, context, context.getRetryIndex() + 1);
            return;
        }
        jobTaskService.finishTaskRun(task.getId(), JobRunStatusEnum.FAILED);
    }

    /**
     * 安排失败重试。
     *
     * <p>第一版先采用同节点延迟重试，确保同一条调度链路上的重试不会与正常 cron 并发。</p>
     *
     * @param task       任务定义
     * @param context    原始执行上下文
     * @param retryIndex 新重试序号
     */
    private void scheduleRetry(SysJobTaskDO task, JobExecuteContext context, int retryIndex) {
        jobTaskService.extendLock(task.getId(), jobNodeContext.nodeId(), calculateLockUntil(task, LocalDateTime.now()));
        jobDelayTaskScheduler.schedule(
                () -> dispatch(task, JobTriggerTypeEnum.RETRY, context.getParamsJson(), context.getOperatorId(), context.getOperatorName(), retryIndex),
                Instant.now().plusSeconds(task.getRetryIntervalSeconds())
        );
    }

    /**
     * 构建执行上下文。
     *
     * @param task         任务定义
     * @param triggerType  触发类型
     * @param paramsJson   参数 JSON
     * @param operatorId   操作人 ID
     * @param operatorName 操作人名称
     * @param retryIndex   重试序号
     * @return 执行上下文
     */
    private JobExecuteContext buildContext(SysJobTaskDO task,
                                           JobTriggerTypeEnum triggerType,
                                           String paramsJson,
                                           String operatorId,
                                           String operatorName,
                                           int retryIndex) {
        JobExecuteContext context = new JobExecuteContext();
        context.setJobId(task.getId());
        context.setJobCode(task.getJobCode());
        context.setJobName(task.getJobName());
        context.setHandlerCode(task.getHandlerCode());
        context.setRunId(UUID.randomUUID().toString().replace("-", ""));
        context.setTriggerType(triggerType);
        context.setSchedulerMode(JobSchedulerModeEnum.valueOf(task.getSchedulerMode()));
        context.setExecuteMode(JobExecuteModeEnum.valueOf(task.getExecuteMode()));
        context.setParamsJson(paramsJson);
        context.setScheduledTime(task.getNextTriggerTime());
        context.setActualTriggerTime(LocalDateTime.now());
        context.setRetryIndex(retryIndex);
        context.setMaxRetryCount(task.getRetryCount());
        context.setOperatorId(operatorId);
        context.setOperatorName(operatorName);
        context.setExecutorNode(jobNodeContext.nodeId());
        context.setTraceId(TraceIdSupport.newTraceId());
        return context;
    }

    /**
     * 计算锁过期时间，覆盖整个执行和重试窗口，避免在重试链未结束前被其他节点再次抢占。
     *
     * @param task        任务定义
     * @param triggerTime 触发时间
     * @return 锁过期时间
     */
    private LocalDateTime calculateLockUntil(SysJobTaskDO task, LocalDateTime triggerTime) {
        int seconds = task.getTimeoutSeconds() * (task.getRetryCount() + 1)
                + task.getRetryIntervalSeconds() * task.getRetryCount()
                + LOCK_BUFFER_SECONDS;
        return triggerTime.plusSeconds(seconds);
    }

    /**
     * 在声明执行模式为 ASYNC 但 Bean 类型未实现 AsyncJobHandler 时抛出异常。
     *
     * @param handler 处理器
     * @return 异步处理器
     */
    private AsyncJobHandler castAsync(JobHandler handler) {
        if (handler instanceof AsyncJobHandler asyncJobHandler) {
            return asyncJobHandler;
        }
        throw new ServiceException(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), "async job handler type mismatch");
    }
}
