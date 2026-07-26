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
     * sys Job Run Log Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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

    @Override
    /**
     * 完成 create Waiting Log 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param task task 输入值，含义由调用方法名称和所属业务对象限定
     * @param context context 输入值，含义由调用方法名称和所属业务对象限定
     * @param maskedParams masked Params 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 推进 mark Running 对应的状态或处理结果，并保留后续查询所需信息。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param logId log Id 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void markRunning(Long logId) {
        SysJobRunLogDO runLog = new SysJobRunLogDO();
        runLog.setId(logId);
        runLog.setRunStatus(JobRunStatusEnum.RUNNING.name());
        runLog.setStartTime(LocalDateTime.now());
        runLog.setUpdateTime(LocalDateTime.now());
        sysJobRunLogMapper.updateById(runLog);
    }

    @Override
    /**
     * 完成 finish As Success 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param logId log Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param durationMs duration Ms 输入值，含义由调用方法名称和所属业务对象限定
     * @param resultMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     */
    public void finishAsSuccess(Long logId, long durationMs, String resultMessage) {
        sysJobRunLogMapper.finishIfRunning(logId, JobRunStatusEnum.SUCCESS.name(), resultMessage, null, durationMs);
    }

    @Override
    /**
     * 完成 finish As Failed 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param logId log Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param durationMs duration Ms 输入值，含义由调用方法名称和所属业务对象限定
     * @param errorMessage 错误提示或消息内容，供异常转换、日志摘要或返回结果使用
     */
    public void finishAsFailed(Long logId, long durationMs, String errorMessage) {
        sysJobRunLogMapper.finishIfRunning(logId, JobRunStatusEnum.FAILED.name(), null, errorMessage, durationMs);
    }

    @Override
    /**
     * 完成 finish As Timeout 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param runLog run Log 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
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
    /**
     * 完成 page Logs 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public PageResult<SysJobRunLogDO> pageLogs(JobRunLogQueryRequest request) {
        JobRunLogQueryRequest query = request == null ? new JobRunLogQueryRequest() : request;
        Page<SysJobRunLogDO> page = sysJobRunLogMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                buildQueryWrapper(query)
        );
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    @Override
    /**
     * 完成 remove Log 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param id id 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void removeLog(Long id) {
        sysJobRunLogMapper.deleteById(id);
    }

    @Override
    /**
     * 完成 clean Logs 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public int cleanLogs(JobRunLogQueryRequest request) {
        return sysJobRunLogMapper.delete(buildQueryWrapper(request == null ? new JobRunLogQueryRequest() : request));
    }

    @Override
    /**
     * 完成 list Logs 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
    public List<SysJobRunLogDO> listLogs(JobRunLogQueryRequest request) {
        return sysJobRunLogMapper.selectList(buildQueryWrapper(request == null ? new JobRunLogQueryRequest() : request));
    }

    @Override
    /**
     * 查询 select Timeout Candidates 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @return 解析或查询得到的业务值
     */
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
