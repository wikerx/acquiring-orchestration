package com.scott.payment.job.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.scott.payment.component.core.enums.ApiResultEnum;
import com.scott.payment.component.core.exception.ServiceException;
import com.scott.payment.component.core.json.JsonUtils;
import com.scott.payment.component.core.model.PageResult;
import com.scott.payment.component.job.enums.JobExecuteModeEnum;
import com.scott.payment.component.job.enums.JobRunStatusEnum;
import com.scott.payment.component.job.enums.JobSchedulerModeEnum;
import com.scott.payment.component.job.enums.JobStatusEnum;
import com.scott.payment.component.job.executor.JobHandlerDescriptor;
import com.scott.payment.job.api.internal.dto.JobTaskQueryRequest;
import com.scott.payment.job.api.internal.dto.JobTaskSaveRequest;
import com.scott.payment.job.entity.SysJobTaskDO;
import com.scott.payment.job.executor.JobHandlerRegistry;
import com.scott.payment.job.mapper.SysJobTaskMapper;
import com.scott.payment.job.service.JobTaskService;
import com.scott.payment.job.service.JobTaskTimingService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskServiceImpl
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务任务服务实现
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskServiceImpl
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Task Service Impl，位于 service-job 的服务实现层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Service
public class JobTaskServiceImpl implements JobTaskService {

    /**
     * 收单支付固定配置或枚举常量，集中维护魔法值，避免业务代码散落硬编码。
     */
    private static final int NOT_DELETED = 0;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final SysJobTaskMapper sysJobTaskMapper;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final JobTaskTimingService jobTaskTimingService;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final JobHandlerRegistry jobHandlerRegistry;

    /**
     * 创建任务定义领域服务。
     *
     * @param sysJobTaskMapper      任务 Mapper
     * @param jobTaskTimingService  调度时间计算服务
     * @param jobHandlerRegistry    处理器注册中心
     */
    public JobTaskServiceImpl(SysJobTaskMapper sysJobTaskMapper,
                              JobTaskTimingService jobTaskTimingService,
                              JobHandlerRegistry jobHandlerRegistry) {
        this.sysJobTaskMapper = sysJobTaskMapper;
        this.jobTaskTimingService = jobTaskTimingService;
        this.jobHandlerRegistry = jobHandlerRegistry;
    }

    /**
     * 查询收单支付列表或分页数据，供页面筛选和展示使用。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public PageResult<SysJobTaskDO> pageTasks(JobTaskQueryRequest request) {
        JobTaskQueryRequest query = request == null ? new JobTaskQueryRequest() : request;
        Page<SysJobTaskDO> page = sysJobTaskMapper.selectPage(
                new Page<>(query.safePageNo(), query.safePageSize()),
                new LambdaQueryWrapper<SysJobTaskDO>()
                        .eq(SysJobTaskDO::getDeleted, NOT_DELETED)
                        .eq(StringUtils.hasText(query.getStatus()), SysJobTaskDO::getStatus, query.getStatus())
                        .eq(StringUtils.hasText(query.getJobCode()), SysJobTaskDO::getJobCode, query.getJobCode())
                        .eq(StringUtils.hasText(query.getJobGroup()), SysJobTaskDO::getJobGroup, query.getJobGroup())
                        .eq(StringUtils.hasText(query.getHandlerCode()), SysJobTaskDO::getHandlerCode, query.getHandlerCode())
                        .like(StringUtils.hasText(query.getJobName()), SysJobTaskDO::getJobName, query.getJobName())
                        .orderByAsc(SysJobTaskDO::getJobGroup)
                        .orderByAsc(SysJobTaskDO::getJobCode)
        );
        return PageResult.of(page.getTotal(), page.getCurrent(), page.getSize(), page.getRecords());
    }

    /**
     * 创建或保存收单支付数据，保持请求校验、默认值和审计字段一致。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public SysJobTaskDO createTask(JobTaskSaveRequest request) {
        validateRequest(request);
        if (existsByJobCode(request.getJobCode(), null)) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "jobCode already exists");
        }
        SysJobTaskDO task = new SysJobTaskDO();
        fillTask(task, request);
        LocalDateTime now = LocalDateTime.now();
        task.setDeleted(NOT_DELETED);
        task.setVersion(0);
        task.setCreateBy(request.getOperator());
        task.setCreateTime(now);
        task.setUpdateBy(request.getOperator());
        task.setUpdateTime(now);
        task.setLastRunStatus(null);
        task.setLastTriggerTime(null);
        task.setLockOwner(null);
        task.setLockUntil(null);
        task.setNextTriggerTime(jobTaskTimingService.calculateNextTriggerTime(task, now));
        sysJobTaskMapper.insert(task);
        return task;
    }

    /**
     * 更新收单支付数据，保持已有记录、状态和审计字段的一致性。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param request 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public SysJobTaskDO updateTask(Long taskId, JobTaskSaveRequest request) {
        validateRequest(request);
        SysJobTaskDO task = getRequiredTask(taskId);
        if (existsByJobCode(request.getJobCode(), taskId)) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "jobCode already exists");
        }
        fillTask(task, request);
        task.setUpdateBy(request.getOperator());
        task.setUpdateTime(LocalDateTime.now());
        task.setNextTriggerTime(jobTaskTimingService.calculateNextTriggerTime(task, LocalDateTime.now()));
        sysJobTaskMapper.updateById(task);
        return task;
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param status 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param operator 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public SysJobTaskDO changeStatus(Long taskId, String status, String operator) {
        SysJobTaskDO task = getRequiredTask(taskId);
        task.setStatus(JobStatusEnum.valueOf(status).name());
        task.setUpdateBy(operator);
        task.setUpdateTime(LocalDateTime.now());
        task.setNextTriggerTime(jobTaskTimingService.calculateNextTriggerTime(task, LocalDateTime.now()));
        sysJobTaskMapper.updateById(task);
        return task;
    }

    /**
     * 删除收单支付数据，按业务规则处理引用校验和删除边界。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param operator 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    public void deleteTask(Long taskId, String operator) {
        SysJobTaskDO task = getRequiredTask(taskId);
        task.setDeleted(1);
        task.setStatus(JobStatusEnum.DISABLED.name());
        task.setUpdateBy(operator);
        task.setUpdateTime(LocalDateTime.now());
        sysJobTaskMapper.updateById(task);
    }

    /**
     * 获取收单支付明细数据，并在不存在或不满足条件时按业务边界处理。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public SysJobTaskDO getRequiredTask(Long taskId) {
        SysJobTaskDO task = sysJobTaskMapper.selectById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() != NOT_DELETED) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "job task not found");
        }
        return task;
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param triggerTime 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param limit 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public List<SysJobTaskDO> selectDueTasks(LocalDateTime triggerTime, int limit) {
        return sysJobTaskMapper.selectDueTasks(triggerTime, limit);
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param task 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param nodeId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param currentTime 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @return 处理后的业务结果或页面展示数据。
     */
    @Override
    public boolean tryAcquireLock(SysJobTaskDO task, String nodeId, LocalDateTime currentTime) {
        LocalDateTime lockUntil = currentTime.plusSeconds(Math.max(task.getTimeoutSeconds(), 30));
        return sysJobTaskMapper.acquireLock(task.getId(), nodeId, lockUntil, task.getVersion()) > 0;
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param lastTriggerTime 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param nextTriggerTime 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    public void markScheduled(Long taskId, LocalDateTime lastTriggerTime, LocalDateTime nextTriggerTime) {
        SysJobTaskDO task = getRequiredTask(taskId);
        task.setLastTriggerTime(lastTriggerTime);
        task.setNextTriggerTime(nextTriggerTime);
        task.setUpdateTime(LocalDateTime.now());
        sysJobTaskMapper.updateById(task);
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param nodeId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param lockUntil 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    public void extendLock(Long taskId, String nodeId, LocalDateTime lockUntil) {
        sysJobTaskMapper.extendLock(taskId, nodeId, lockUntil);
    }

    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     * @param taskId 请求参数或业务处理上下文，不能为空时由上层校验约束。
     * @param lastRunStatus 请求参数或业务处理上下文，不能为空时由上层校验约束。
     */
    @Override
    public void finishTaskRun(Long taskId, JobRunStatusEnum lastRunStatus) {
        SysJobTaskDO task = getRequiredTask(taskId);
        task.setLastRunStatus(lastRunStatus.name());
        task.setLockOwner(null);
        task.setLockUntil(null);
        task.setUpdateTime(LocalDateTime.now());
        sysJobTaskMapper.updateById(task);
    }

    /**
     * 填充任务可编辑字段。
     *
     * @param task    任务实体
     * @param request 保存请求
     */
    private void fillTask(SysJobTaskDO task, JobTaskSaveRequest request) {
        JobHandlerDescriptor descriptor = jobHandlerRegistry.getRequiredDescriptor(request.getHandlerCode());
        validateJson(request.getParams());
        task.setJobCode(request.getJobCode().trim());
        task.setJobName(request.getJobName().trim());
        task.setJobGroup(request.getJobGroup().trim());
        task.setHandlerCode(request.getHandlerCode().trim());
        task.setCronExpression(trimToNull(request.getCronExpression()));
        task.setSchedulerMode(JobSchedulerModeEnum.valueOf(request.getSchedulerMode()).name());
        task.setTriggerMode(request.getTriggerMode().trim());
        task.setExecuteMode(descriptor.getExecuteMode().name());
        task.setRouteStrategy("LOCAL");
        task.setMisfireStrategy(request.getMisfireStrategy().trim());
        task.setTimeoutSeconds(defaultIfNull(request.getTimeoutSeconds(), 300));
        task.setRetryCount(defaultIfNull(request.getRetryCount(), 0));
        task.setRetryIntervalSeconds(defaultIfNull(request.getRetryIntervalSeconds(), 60));
        task.setAllowConcurrent(defaultIfNull(request.getAllowConcurrent(), Boolean.TRUE.equals(descriptor.getAllowConcurrent()) ? 1 : 0));
        task.setParams(trimToNull(request.getParams()));
        task.setStatus(JobStatusEnum.valueOf(request.getStatus()).name());
        task.setDescription(trimToNull(request.getDescription()));
    }

    /**
     * 校验保存请求。
     *
     * @param request 保存请求
     */
    private void validateRequest(JobTaskSaveRequest request) {
        JobStatusEnum.valueOf(request.getStatus());
        JobSchedulerModeEnum.valueOf(request.getSchedulerMode());
        if (StringUtils.hasText(request.getCronExpression())) {
            jobTaskTimingService.calculateNextTriggerTime(buildPreviewTask(request), LocalDateTime.now());
        }
    }

    /**
     * 构建仅用于预校验的任务实体。
     *
     * @param request 保存请求
     * @return 预校验实体
     */
    private SysJobTaskDO buildPreviewTask(JobTaskSaveRequest request) {
        SysJobTaskDO task = new SysJobTaskDO();
        fillTask(task, request);
        return task;
    }

    /**
     * 按任务编码检查是否已存在。
     *
     * @param jobCode           任务编码
     * @param excludeTaskId     需要排除的任务 ID
     * @return true 表示已存在
     */
    private boolean existsByJobCode(String jobCode, Long excludeTaskId) {
        return sysJobTaskMapper.selectCount(new LambdaQueryWrapper<SysJobTaskDO>()
                .eq(SysJobTaskDO::getDeleted, NOT_DELETED)
                .eq(SysJobTaskDO::getJobCode, jobCode)
                .ne(excludeTaskId != null, SysJobTaskDO::getId, excludeTaskId)) > 0;
    }

    /**
     * 校验任务参数 JSON。
     *
     * @param paramsJson 参数 JSON
     */
    private void validateJson(String paramsJson) {
        if (!StringUtils.hasText(paramsJson)) {
            return;
        }
        try {
            JsonUtils.parseObject(paramsJson, Object.class);
        } catch (Exception exception) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "job params must be valid json");
        }
    }

    /**
     * 将空白字符串转为 null。
     *
     * @param value 原始值
     * @return 处理后的值
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 获取默认整数值。
     *
     * @param value        原始值
     * @param defaultValue 默认值
     * @return 最终值
     */
    private Integer defaultIfNull(Integer value, Integer defaultValue) {
        return value == null ? defaultValue : value;
    }
}
