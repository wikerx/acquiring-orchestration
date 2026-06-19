package com.scott.payment.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.job.enums.JobRunStatusEnum;
import com.scott.payment.component.job.executor.JobExecuteContext;
import com.scott.payment.job.api.internal.dto.JobRunLogQueryRequest;
import com.scott.payment.job.entity.SysJobRunLogDO;
import com.scott.payment.job.entity.SysJobTaskDO;
import com.scott.payment.job.mapper.SysJobRunLogMapper;
import com.scott.payment.job.service.JobRunLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunLogServiceImpl
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务运行日志服务实现
 * @status : create
 */

@Service
public class JobRunLogServiceImpl implements JobRunLogService {

    private final SysJobRunLogMapper sysJobRunLogMapper;

    /**
     * 创建执行日志领域服务。
     *
     * @param sysJobRunLogMapper 执行日志 Mapper
     */
    public JobRunLogServiceImpl(SysJobRunLogMapper sysJobRunLogMapper) {
        this.sysJobRunLogMapper = sysJobRunLogMapper;
    }

    @Override
    public SysJobRunLogDO createWaitingLog(SysJobTaskDO task, JobExecuteContext context, String maskedParams) {
        LocalDateTime now = LocalDateTime.now();
        SysJobRunLogDO runLog = new SysJobRunLogDO();
        runLog.setRunId(context.getRunId());
        runLog.setJobId(task.getId());
        runLog.setJobCode(task.getJobCode());
        runLog.setJobName(task.getJobName());
        runLog.setHandlerCode(task.getHandlerCode());
        runLog.setTriggerType(context.getTriggerType().name());
        runLog.setSchedulerMode(context.getSchedulerMode().name());
        runLog.setExecuteMode(context.getExecuteMode().name());
        runLog.setExecutorNode(context.getExecutorNode());
        runLog.setRunStatus(JobRunStatusEnum.WAITING.name());
        runLog.setRetryIndex(context.getRetryIndex());
        runLog.setMaxRetryCount(context.getMaxRetryCount());
        runLog.setTimeoutSeconds(task.getTimeoutSeconds());
        runLog.setParamsSnapshot(maskedParams);
        runLog.setTraceId(context.getTraceId());
        runLog.setOperatorId(context.getOperatorId());
        runLog.setOperatorName(context.getOperatorName());
        runLog.setCreateTime(now);
        runLog.setUpdateTime(now);
        sysJobRunLogMapper.insert(runLog);
        return runLog;
    }

    @Override
    public void markRunning(Long logId) {
        SysJobRunLogDO runLog = new SysJobRunLogDO();
        runLog.setId(logId);
        runLog.setRunStatus(JobRunStatusEnum.RUNNING.name());
        runLog.setStartTime(LocalDateTime.now());
        runLog.setUpdateTime(LocalDateTime.now());
        sysJobRunLogMapper.updateById(runLog);
    }

    @Override
    public void finishAsSuccess(Long logId, long durationMs, String resultMessage) {
        sysJobRunLogMapper.finishIfRunning(logId, JobRunStatusEnum.SUCCESS.name(), resultMessage, null, durationMs);
    }

    @Override
    public void finishAsFailed(Long logId, long durationMs, String errorMessage) {
        sysJobRunLogMapper.finishIfRunning(logId, JobRunStatusEnum.FAILED.name(), null, errorMessage, durationMs);
    }

    @Override
    public boolean finishAsTimeout(SysJobRunLogDO runLog) {
        long durationMs = runLog.getStartTime() == null ? 0L
                : java.time.Duration.between(runLog.getStartTime(), LocalDateTime.now()).toMillis();
        return sysJobRunLogMapper.finishIfRunning(
                runLog.getId(),
                JobRunStatusEnum.TIMEOUT.name(),
                null,
                "job execution timeout",
                durationMs
        ) > 0;
    }

    @Override
    public PageResult<SysJobRunLogDO> pageLogs(JobRunLogQueryRequest request) {
        JobRunLogQueryRequest query = request == null ? new JobRunLogQueryRequest() : request;
        Page<SysJobRunLogDO> page = sysJobRunLogMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                new LambdaQueryWrapper<SysJobRunLogDO>()
                        .eq(query.getJobId() != null, SysJobRunLogDO::getJobId, query.getJobId())
                        .eq(StringUtils.hasText(query.getJobCode()), SysJobRunLogDO::getJobCode, query.getJobCode())
                        .eq(StringUtils.hasText(query.getRunStatus()), SysJobRunLogDO::getRunStatus, query.getRunStatus())
                        .eq(StringUtils.hasText(query.getTriggerType()), SysJobRunLogDO::getTriggerType, query.getTriggerType())
                        .eq(StringUtils.hasText(query.getExecutorNode()), SysJobRunLogDO::getExecutorNode, query.getExecutorNode())
                        .orderByDesc(SysJobRunLogDO::getCreateTime)
                        .orderByDesc(SysJobRunLogDO::getId)
        );
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    @Override
    public List<SysJobRunLogDO> selectTimeoutCandidates() {
        return sysJobRunLogMapper.selectTimeoutCandidates();
    }
}
