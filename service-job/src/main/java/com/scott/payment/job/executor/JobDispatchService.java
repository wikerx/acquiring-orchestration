package com.scott.payment.job.executor;

import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.job.enums.JobExecuteModeEnum;
import com.scott.payment.component.job.enums.JobRunStatusEnum;
import com.scott.payment.component.job.enums.JobSchedulerModeEnum;
import com.scott.payment.component.job.enums.JobStatusEnum;
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
import lombok.extern.slf4j.Slf4j;
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
import java.util.function.Supplier;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobDispatchService
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 调度中心任务分发编排服务
 * @status : create
 */
@Slf4j
@Service
public class JobDispatchService {

    /**
     * LOCK BUFFER SECONDS，用于保存 Job Dispatch Service 中与 lockbufferseconds 相关的业务属性。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private static final int LOCK_BUFFER_SECONDS = 30;

    /**
     * job Task Service 依赖，用于 Job Dispatch Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final JobTaskService jobTaskService;
    /**
     * job Run Log Service 依赖，用于 Job Dispatch Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final JobRunLogService jobRunLogService;
    /**
     * job Task Timing Service 依赖，用于 Job Dispatch Service 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final JobTaskTimingService jobTaskTimingService;
    /**
     * job Handler Registry，用于保存 Job Dispatch Service 中与 jobhandlerregistry 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final JobHandlerRegistry jobHandlerRegistry;
    /**
     * job Future Registry，用于保存 Job Dispatch Service 中与 jobfutureregistry 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final JobFutureRegistry jobFutureRegistry;
    /**
     * job Task Executor，用于保存 Job Dispatch Service 中与 jobtaskexecutor 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ThreadPoolTaskExecutor jobTaskExecutor;
    /**
     * job Delay Task Scheduler，用于保存 Job Dispatch Service 中与 jobdelaytaskscheduler 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final ThreadPoolTaskScheduler jobDelayTaskScheduler;
    /**
     * job Node Context，用于保存 Job Dispatch Service 中与 jobnodecontext 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
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
        dispatch(task, JobTriggerTypeEnum.SCHEDULE, task.getParams(), null, null, 0, null);
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
        if (!JobStatusEnum.ENABLED.name().equals(task.getStatus())) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "job task is disabled");
        }
        LocalDateTime triggerTime = LocalDateTime.now();
        boolean acquired = jobTaskService.tryAcquireLock(task, jobNodeContext.nodeId(), triggerTime);
        if (!acquired) {
            SysJobTaskDO latestTask = jobTaskService.getRequiredTask(taskId);
            String message = JobStatusEnum.ENABLED.name().equals(latestTask.getStatus())
                    ? "job task is already running"
                    : "job task is disabled";
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), message);
        }
        String paramsJson = request.getParamsJson() == null || request.getParamsJson().isBlank()
                ? task.getParams()
                : request.getParamsJson();
        return dispatch(task, JobTriggerTypeEnum.MANUAL, paramsJson, request.getOperatorId(), request.getOperatorName(), 0, null);
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
            finishTaskRun(runLog.getJobId(), JobRunStatusEnum.TIMEOUT, runLog.getRunId());
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
     * @param traceId      重试链路沿用的 traceId，首次执行为空时生成新值
     * @return 执行批次号
     */
    private String dispatch(SysJobTaskDO task,
                            JobTriggerTypeEnum triggerType,
                            String paramsJson,
                            String operatorId,
                            String operatorName,
                            int retryIndex,
                            String traceId) {
        JobHandlerDescriptor descriptor = jobHandlerRegistry.getRequiredDescriptor(task.getHandlerCode());
        if (triggerType == JobTriggerTypeEnum.MANUAL && Boolean.FALSE.equals(descriptor.getAllowManualTrigger())) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "job handler does not allow manual trigger");
        }
        JobExecuteContext context = buildContext(task, triggerType, paramsJson, operatorId, operatorName, retryIndex, traceId);
        LocalDateTime triggerTime = context.getActualTriggerTime();
        jobTaskService.extendLock(task.getId(), jobNodeContext.nodeId(), calculateLockUntil(task, triggerTime));
        if (triggerType == JobTriggerTypeEnum.SCHEDULE) {
            jobTaskService.markScheduled(task.getId(), triggerTime, jobTaskTimingService.calculateNextTriggerTime(task, triggerTime));
        }
        SysJobRunLogDO runLog = jobRunLogService.createWaitingLog(task, context, JobParameterMasker.mask(paramsJson));
        jobRunLogService.markRunning(runLog.getId());
        logJobStart(context);
        JobHandler handler = jobHandlerRegistry.getRequiredHandler(task.getHandlerCode());
        AsyncJobHandler asyncJobHandler = handler instanceof AsyncJobHandler
                ? (AsyncJobHandler) handler
                : null;
        if (asyncJobHandler != null || descriptor.getExecuteMode() == JobExecuteModeEnum.ASYNC) {
            executeAsync(task, context, runLog, asyncJobHandler != null ? asyncJobHandler : castAsync(handler));
        } else if (triggerType == JobTriggerTypeEnum.MANUAL) {
            executeDetachedSync(task, context, runLog, handler);
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
        CompletableFuture<JobExecuteResult> future = submitSyncHandler(context, handler);
        jobFutureRegistry.register(context.getRunId(), future);
        try {
            JobExecuteResult result = future.get(task.getTimeoutSeconds(), TimeUnit.SECONDS);
            finishSuccessOrFailure(task, runLog, context, result, start);
        } catch (TimeoutException exception) {
            future.cancel(true);
            markTimeout(runLog);
            logJobEnd(context, Duration.between(start, Instant.now()).toMillis(), JobRunStatusEnum.TIMEOUT.name(), exception.getMessage());
        } catch (Exception exception) {
            JobExecuteResult result = JobExecuteResult.failed(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), exception.getMessage());
            finishSuccessOrFailure(task, runLog, context, result, start);
        } finally {
            jobFutureRegistry.unregister(context.getRunId());
        }
    }

    /**
     * 手动触发同步处理器时把执行留在任务线程池，HTTP 请求立即返回 runId。
     * 执行结果仍由统一运行日志和超时扫描器收口，任务锁在最终状态写入后释放。
     */
    private void executeDetachedSync(SysJobTaskDO task,
                                     JobExecuteContext context,
                                     SysJobRunLogDO runLog,
                                     JobHandler handler) {
        Instant start = Instant.now();
        CompletableFuture<JobExecuteResult> future = submitSyncHandler(context, handler);
        jobFutureRegistry.register(context.getRunId(), future);
        future.whenComplete((result, throwable) -> runWithTrace(context, () -> {
            try {
                if (throwable != null) {
                    finishSuccessOrFailure(task, runLog, context,
                            JobExecuteResult.failed(ApiResultEnum.INTERNAL_SERVER_ERROR.getCode(), throwable.getMessage()),
                            start);
                    return;
                }
                finishSuccessOrFailure(task, runLog, context, result, start);
            } finally {
                jobFutureRegistry.unregister(context.getRunId());
            }
        }));
    }

    /** 提交带 traceId 生命周期的同步处理器。 */
    private CompletableFuture<JobExecuteResult> submitSyncHandler(JobExecuteContext context, JobHandler handler) {
        return CompletableFuture.supplyAsync(() -> {
            TraceIdSupport.bindTraceId(context.getTraceId());
            try {
                return handler.execute(context);
            } finally {
                TraceIdSupport.clear();
            }
        }, jobTaskExecutor);
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
            future = callWithTrace(context, () -> handler.executeAsync(context));
        } catch (Exception exception) {
            future = CompletableFuture.failedFuture(exception);
        }
        jobFutureRegistry.register(context.getRunId(), future);
        future.whenComplete((result, throwable) -> {
            runWithTrace(context, () -> {
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
            logJobEnd(context, durationMs, "ACCEPTED", null);
            return;
        }
        if (result != null && result.isSuccess()) {
            jobRunLogService.finishAsSuccess(runLog.getId(), durationMs, result.getMessage());
            finishTaskRun(task.getId(), JobRunStatusEnum.SUCCESS, context.getRunId());
            logJobEnd(context, durationMs, JobRunStatusEnum.SUCCESS.name(), null);
            return;
        }
        String failureMessage = result == null ? "job execute result is null"
                : (result.getErrorMessage() == null || result.getErrorMessage().isBlank() ? result.getMessage() : result.getErrorMessage());
        jobRunLogService.finishAsFailed(runLog.getId(), durationMs, failureMessage);
        if (context.getRetryIndex() < task.getRetryCount()) {
            if (scheduleRetry(task, context, context.getRetryIndex() + 1)) {
                logJobEnd(context, durationMs, JobRunStatusEnum.FAILED.name(), failureMessage);
                return;
            }
        }
        finishTaskRun(task.getId(), JobRunStatusEnum.FAILED, context.getRunId());
        logJobEnd(context, durationMs, JobRunStatusEnum.FAILED.name(), failureMessage);
    }

    /** 释放当前节点持有的任务锁；锁已转移时保留新持有者并记录可观测告警。 */
    private void finishTaskRun(Long taskId, JobRunStatusEnum status, String runId) {
        if (!jobTaskService.finishTaskRun(taskId, status, jobNodeContext.nodeId())) {
            log.warn("event: JOB_TASK_LOCK_RELEASE_SKIPPED jobId: {} runId: {} status: {} nodeId: {}",
                    taskId, runId, status.name(), jobNodeContext.nodeId());
        }
    }

    /**
     * 安排失败重试。
     *
     * <p>第一版先采用同节点延迟重试，确保同一条调度链路上的重试不会与正常 cron 并发。</p>
     *
     * @param task       任务定义
     * @param context    原始执行上下文
     * @param retryIndex 新重试序号
     * @return true 表示延迟重试已成功提交，false 表示提交失败且应立即释放任务锁
     */
    private boolean scheduleRetry(SysJobTaskDO task, JobExecuteContext context, int retryIndex) {
        try {
            jobTaskService.extendLock(task.getId(), jobNodeContext.nodeId(), calculateLockUntil(task, LocalDateTime.now()));
            jobDelayTaskScheduler.schedule(
                    () -> dispatch(task, JobTriggerTypeEnum.RETRY, context.getParamsJson(), context.getOperatorId(), context.getOperatorName(), retryIndex, context.getTraceId()),
                    Instant.now().plusSeconds(task.getRetryIntervalSeconds())
            );
            runWithTrace(context, () -> log.info("event: JOB_RETRY_SCHEDULED traceId: {} jobId: {} handler: {} runId: {} retryIndex: {} nextRetryIndex: {} shardIndex: {} shardTotal: {}",
                    context.getTraceId(),
                    context.getJobId(),
                    context.getHandlerCode(),
                    context.getRunId(),
                    context.getRetryIndex(),
                    retryIndex,
                    context.getShardIndex(),
                    context.getShardTotal()));
            return true;
        } catch (RuntimeException exception) {
            runWithTrace(context, () -> log.error("event: JOB_RETRY_SCHEDULE_FAILED traceId: {} jobId: {} handler: {} runId: {} retryIndex: {} nextRetryIndex: {} nodeId: {}",
                    context.getTraceId(),
                    context.getJobId(),
                    context.getHandlerCode(),
                    context.getRunId(),
                    context.getRetryIndex(),
                    retryIndex,
                    jobNodeContext.nodeId(),
                    exception));
            return false;
        }
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
     * @param traceId      重试链路沿用的 traceId，首次执行为空时生成新值
     * @return 执行上下文
     */
    private JobExecuteContext buildContext(SysJobTaskDO task,
                                           JobTriggerTypeEnum triggerType,
                                           String paramsJson,
                                           String operatorId,
                                           String operatorName,
                                           int retryIndex,
                                           String traceId) {
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
        context.setShardIndex(0);
        context.setShardTotal(1);
        context.setOperatorId(operatorId);
        context.setOperatorName(operatorName);
        context.setExecutorNode(jobNodeContext.nodeId());
        context.setTraceId(traceId == null || traceId.isBlank() ? TraceIdSupport.newTraceId() : traceId);
        return context;
    }

    /**
     * 记录调度任务执行开始事件。
     *
     * @param context 任务执行上下文
     */
    private void logJobStart(JobExecuteContext context) {
        runWithTrace(context, () -> log.info("event: JOB_EXECUTE_START traceId: {} jobId: {} handler: {} runId: {} triggerType: {} retryIndex: {} shardIndex: {} shardTotal: {}",
                context.getTraceId(),
                context.getJobId(),
                context.getHandlerCode(),
                context.getRunId(),
                context.getTriggerType(),
                context.getRetryIndex(),
                context.getShardIndex(),
                context.getShardTotal()));
    }

    /**
     * 记录调度任务执行结束事件。
     *
     * @param context        任务执行上下文
     * @param durationMs     执行耗时，单位毫秒
     * @param status         执行状态
     * @param failureMessage 失败原因摘要
     */
    private void logJobEnd(JobExecuteContext context, long durationMs, String status, String failureMessage) {
        runWithTrace(context, () -> log.info("event: JOB_EXECUTE_END traceId: {} jobId: {} handler: {} runId: {} status: {} retryIndex: {} shardIndex: {} shardTotal: {} durationMs: {} failureMessage: {}",
                context.getTraceId(),
                context.getJobId(),
                context.getHandlerCode(),
                context.getRunId(),
                status,
                context.getRetryIndex(),
                context.getShardIndex(),
                context.getShardTotal(),
                durationMs,
                failureMessage));
    }

    /**
     * 在当前线程临时绑定任务 traceId 并执行逻辑，执行完成后恢复原 traceId。
     *
     * @param context 任务执行上下文
     * @param action  需要记录任务链路的逻辑
     */
    private void runWithTrace(JobExecuteContext context, Runnable action) {
        callWithTrace(context, () -> {
            action.run();
            return null;
        });
    }

    /**
     * 在当前线程临时绑定任务 traceId 并返回调用结果，执行完成后恢复原 traceId。
     *
     * @param context  任务执行上下文
     * @param supplier 需要记录任务链路的逻辑
     * @param <T>      返回值类型
     * @return 调用结果
     */
    private <T> T callWithTrace(JobExecuteContext context, Supplier<T> supplier) {
        String previousTraceId = TraceContext.getTraceId();
        TraceIdSupport.bindTraceId(context.getTraceId());
        try {
            return supplier.get();
        } finally {
            TraceIdSupport.clear();
            TraceIdSupport.bindTraceId(previousTraceId);
        }
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
