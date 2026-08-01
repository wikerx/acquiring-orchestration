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

    /**
     * sys Job Run Log Mapper 依赖，用于 Job Run Log Service Impl 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
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
     * 在任务提交执行前创建 WAITING 日志快照。
     * <p>
     * 参数只能传入调用方已经脱敏的摘要；运行标识、重试序号、超时和 traceId 用于后续
     * 状态 CAS 与审计追踪。
     * </p>
     *
     * @param task         任务定义
     * @param context      本次执行上下文
     * @param maskedParams 已脱敏参数摘要
     * @return 已持久化的运行日志
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
     * 将已提交的运行日志标记为 RUNNING 并记录真实开始时间。
     *
     * @param logId 运行日志主键
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
     * 仅在日志仍为 RUNNING 时落成功终态。
     *
     * @param logId         运行日志主键
     * @param durationMs    执行耗时，单位毫秒
     * @param resultMessage 非敏感结果摘要
     */
    @Override
    public void finishAsSuccess(Long logId, long durationMs, String resultMessage) {
        sysJobRunLogMapper.finishIfRunning(logId, JobRunStatusEnum.SUCCESS.name(), resultMessage, null, durationMs);
    }

    /**
     * 仅在日志仍为 RUNNING 时落失败终态。
     *
     * @param logId        运行日志主键
     * @param durationMs   执行耗时，单位毫秒
     * @param errorMessage 已截断且不含敏感参数的错误摘要
     */
    @Override
    public void finishAsFailed(Long logId, long durationMs, String errorMessage) {
        sysJobRunLogMapper.finishIfRunning(logId, JobRunStatusEnum.FAILED.name(), null, errorMessage, durationMs);
    }

    /**
     * 使用 RUNNING 条件更新将超时候选日志切换为 TIMEOUT。
     * <p>
     * 成功或失败等既有终态不会被超时扫描覆盖。
     * </p>
     *
     * @param runLog 超时候选日志
     * @return 本次 CAS 成功写入 TIMEOUT 时返回 {@code true}
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
     * 按任务、状态、触发类型和节点分页查询运行日志。
     *
     * @param request 查询条件
     * @return 按创建时间和主键倒序的分页结果
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
     * 按主键删除单条运行日志。
     *
     * @param id 日志主键
     */
    @Override
    public void removeLog(Long id) {
        sysJobRunLogMapper.deleteById(id);
    }

    /**
     * 按显式查询条件批量清理运行日志。
     *
     * @param request 清理范围
     * @return 删除行数
     */
    @Override
    public int cleanLogs(JobRunLogQueryRequest request) {
        return sysJobRunLogMapper.delete(buildQueryWrapper(request == null ? new JobRunLogQueryRequest() : request));
    }

    /**
     * 查询符合条件的运行日志列表，供受控导出使用。
     *
     * @param request 查询条件
     * @return 按创建时间和主键倒序的日志列表
     */
    @Override
    public List<SysJobRunLogDO> listLogs(JobRunLogQueryRequest request) {
        return sysJobRunLogMapper.selectList(buildQueryWrapper(request == null ? new JobRunLogQueryRequest() : request));
    }

    /**
     * 查询最多一批仍为 RUNNING 且超过各自 timeoutSeconds 的日志。
     *
     * @return 按开始时间升序排列的超时候选列表
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
