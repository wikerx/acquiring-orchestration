package com.scott.payment.job.service.impl;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.scott.payment.component.db.constant.DataSourceName;
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
@Service
public class JobTaskServiceImpl implements JobTaskService {

    /**
     * {@code NOT_DELETED}常量，统一 {@code JobTaskServiceImpl} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：个或次；格式：整数；不允许为空；非敏感字段。
     * 取值范围：取值范围由数据库字段、校验注解或任务参数限制；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private static final int NOT_DELETED = 0;

    private final SysJobTaskMapper sysJobTaskMapper;
    private final JobTaskTimingService jobTaskTimingService;
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
     * 分页查询未逻辑删除的任务定义。
     *
     * @param request 状态、任务编码、分组、处理器和名称查询条件
     * @return 按分组和任务编码升序排列的分页结果
     */
    @Override
    @DS(DataSourceName.SLAVE)
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
     * 创建唯一任务编码的调度定义并计算首次触发时间。
     * <p>
     * 新任务不继承任何运行状态或锁持有者；Cron、处理器和重试等规则在写库前统一校验。
     * </p>
     *
     * @param request 任务保存请求
     * @return 已落库的任务定义
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
     * 更新任务可编辑配置并重新计算下一触发时间。
     *
     * @param taskId  待更新任务主键
     * @param request 任务保存请求
     * @return 更新后的任务定义
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
     * 切换任务启停状态并同步下一触发时间。
     *
     * @param taskId   任务主键
     * @param status   {@link JobStatusEnum} 名称
     * @param operator 操作人
     * @return 更新后的任务定义
     */
    @Override
    public SysJobTaskDO changeStatus(Long taskId, String status, String operator) {
        SysJobTaskDO task = getRequiredTask(taskId);
        task.setStatus(JobStatusEnum.valueOf(status).name());
        LocalDateTime now = LocalDateTime.now();
        task.setUpdateBy(operator);
        task.setUpdateTime(now);
        task.setNextTriggerTime(jobTaskTimingService.calculateNextTriggerTime(task, now));
        int updated = sysJobTaskMapper.updateStatus(
                task.getId(), task.getStatus(), task.getNextTriggerTime(), operator, now);
        if (updated != 1) {
            throw new ServiceException(ApiResultEnum.BAD_REQUEST.getCode(), "job task status update failed");
        }
        return task;
    }

    /**
     * 逻辑删除并停用任务，保留历史运行日志和审计记录。
     *
     * @param taskId   任务主键
     * @param operator 操作人
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
     * 查询仍有效的任务定义。
     *
     * @param taskId 任务主键
     * @return 未逻辑删除的任务
     * @throws ServiceException 任务不存在或已删除时抛出
     */
    @Override
    @DS(DataSourceName.MASTER)
    public SysJobTaskDO getRequiredTask(Long taskId) {
        SysJobTaskDO task = sysJobTaskMapper.selectById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() != NOT_DELETED) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "job task not found");
        }
        return task;
    }

    /**
     * 查询指定时间前已到期且可抢占的任务。
     *
     * @param triggerTime 调度扫描时间
     * @param limit       单批最大任务数
     * @return 到期任务列表
     */
    @Override
    @DS(DataSourceName.MASTER)
    public List<SysJobTaskDO> selectDueTasks(LocalDateTime triggerTime, int limit) {
        return sysJobTaskMapper.selectDueTasks(triggerTime, limit);
    }

    /**
     * 使用任务版本和过期锁条件尝试取得数据库调度锁。
     * <p>
     * 锁租期至少 30 秒，较长任务使用其 timeoutSeconds；并发节点只有一个 CAS 更新成功。
     * </p>
     *
     * @param task        扫描到的任务快照
     * @param nodeId      当前执行节点
     * @param currentTime 抢锁基准时间
     * @return 当前节点成功更新锁时返回 {@code true}
     */
    @Override
    public boolean tryAcquireLock(SysJobTaskDO task, String nodeId, LocalDateTime currentTime) {
        LocalDateTime lockUntil = currentTime.plusSeconds(Math.max(task.getTimeoutSeconds(), 30));
        return sysJobTaskMapper.acquireLock(task.getId(), nodeId, lockUntil, task.getVersion()) > 0;
    }

    /**
     * 记录本次计划触发时间和下一次 Cron 触发时间。
     *
     * @param taskId          任务主键
     * @param lastTriggerTime 本次计划触发时间
     * @param nextTriggerTime 下一计划触发时间
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
     * 仅由当前锁持有节点延长任务租期。
     *
     * @param taskId   任务主键
     * @param nodeId   当前锁持有节点
     * @param lockUntil 新的锁过期时间
     */
    @Override
    public void extendLock(Long taskId, String nodeId, LocalDateTime lockUntil) {
        sysJobTaskMapper.extendLock(taskId, nodeId, lockUntil);
    }

    /**
     * 仅由当前持锁节点写入任务终态并显式将锁字段更新为 NULL。
     *
     * @param taskId 任务主键
     * @param lastRunStatus 最终运行状态
     * @param nodeId 当前锁持有节点
     * @return true 表示终态和锁均已更新
     */
    @Override
    public boolean finishTaskRun(Long taskId, JobRunStatusEnum lastRunStatus, String nodeId) {
        return sysJobTaskMapper.finishTaskRun(
                taskId,
                nodeId,
                lastRunStatus.name(),
                LocalDateTime.now()) == 1;
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
