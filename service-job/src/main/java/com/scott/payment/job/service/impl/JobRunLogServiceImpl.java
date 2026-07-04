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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunLogServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Run Log Service Impl，位于 service-job 的服务实现层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class JobRunLogServiceImpl implements JobRunLogService {

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysJobRunLogMapper sysJobRunLogMapper;

    /**
     * 创建执行日志领域服务。
     *
     * @param sysJobRunLogMapper 执行日志 Mapper
     */
    public JobRunLogServiceImpl(SysJobRunLogMapper sysJobRunLogMapper) {
        this.sysJobRunLogMapper = sysJobRunLogMapper;
    }

    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param task 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param context 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param maskedParams 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param logId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    public void markRunning(Long logId) {
        SysJobRunLogDO runLog = new SysJobRunLogDO();
        runLog.setId(logId);
        runLog.setRunStatus(JobRunStatusEnum.RUNNING.name());
        runLog.setStartTime(LocalDateTime.now());
        runLog.setUpdateTime(LocalDateTime.now());
        sysJobRunLogMapper.updateById(runLog);
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param logId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param durationMs 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param resultMessage 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    public void finishAsSuccess(Long logId, long durationMs, String resultMessage) {
        sysJobRunLogMapper.finishIfRunning(logId, JobRunStatusEnum.SUCCESS.name(), resultMessage, null, durationMs);
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param logId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param durationMs 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param errorMessage 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    public void finishAsFailed(Long logId, long durationMs, String errorMessage) {
        sysJobRunLogMapper.finishIfRunning(logId, JobRunStatusEnum.FAILED.name(), null, errorMessage, durationMs);
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param runLog 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
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

    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public PageResult<SysJobRunLogDO> pageLogs(JobRunLogQueryRequest request) {
        JobRunLogQueryRequest query = request == null ? new JobRunLogQueryRequest() : request;
        Page<SysJobRunLogDO> page = sysJobRunLogMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildQueryWrapper(query)
        );
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /**
     * 删除收单支付数据，按业务规则处理引用校验和删除边界。
     * @param id 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    public void removeLog(Long id) {
        sysJobRunLogMapper.deleteById(id);
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public int cleanLogs(JobRunLogQueryRequest request) {
        return sysJobRunLogMapper.delete(buildQueryWrapper(request == null ? new JobRunLogQueryRequest() : request));
    }

    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public List<SysJobRunLogDO> listLogs(JobRunLogQueryRequest request) {
        return sysJobRunLogMapper.selectList(buildQueryWrapper(request == null ? new JobRunLogQueryRequest() : request));
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public List<SysJobRunLogDO> selectTimeoutCandidates() {
        return sysJobRunLogMapper.selectTimeoutCandidates();
    }

    /**
     * 构造运行日志查询条件。
     *
     * @param query 查询条件
     * @return 查询包装器
     */
    private LambdaQueryWrapper<SysJobRunLogDO> buildQueryWrapper(JobRunLogQueryRequest query) {
        return new LambdaQueryWrapper<SysJobRunLogDO>()
                .eq(query.getJobId() != null, SysJobRunLogDO::getJobId, query.getJobId())
                .eq(StringUtils.hasText(query.getJobCode()), SysJobRunLogDO::getJobCode, query.getJobCode())
                .eq(StringUtils.hasText(query.getRunStatus()), SysJobRunLogDO::getRunStatus, query.getRunStatus())
                .eq(StringUtils.hasText(query.getTriggerType()), SysJobRunLogDO::getTriggerType, query.getTriggerType())
                .eq(StringUtils.hasText(query.getExecutorNode()), SysJobRunLogDO::getExecutorNode, query.getExecutorNode())
                .orderByDesc(SysJobRunLogDO::getCreateTime)
                .orderByDesc(SysJobRunLogDO::getId);
    }
}
