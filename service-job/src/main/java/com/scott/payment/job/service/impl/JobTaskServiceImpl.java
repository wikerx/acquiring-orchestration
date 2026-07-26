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
@Service
public class JobTaskServiceImpl implements JobTaskService {

    /**
     * NOT DELETED 常量，用于在当前模块内统一引用固定配置、状态或协议字段。
     * <p>
     * 单位：个；格式：整数；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private static final int NOT_DELETED = 0;

    /**
     * sys Job Task Mapper 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final SysJobTaskMapper sysJobTaskMapper;
    /**
     * job Task Timing Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final JobTaskTimingService jobTaskTimingService;
    /**
     * job Handler Registry 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
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

    @Override
    /**
     * 完成 page Tasks 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
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

    @Override
    /**
     * 完成 create Task 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
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

    @Override
    /**
     * 写入或更新 update Task 相关数据，保持数据库记录与当前业务处理结果一致。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param taskId task Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param request request 对象，携带当前业务动作的输入字段，调用前需满足对应校验注解和协议约束
     * @return 当前方法计算或转换后的业务结果
     */
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

    @Override
    /**
     * 完成 change Status 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param taskId task Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param status 状态编码，取值必须来自对应枚举或数据库受控字典
     * @param operator operator 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public SysJobTaskDO changeStatus(Long taskId, String status, String operator) {
        SysJobTaskDO task = getRequiredTask(taskId);
        task.setStatus(JobStatusEnum.valueOf(status).name());
        task.setUpdateBy(operator);
        task.setUpdateTime(LocalDateTime.now());
        task.setNextTriggerTime(jobTaskTimingService.calculateNextTriggerTime(task, LocalDateTime.now()));
        sysJobTaskMapper.updateById(task);
        return task;
    }

    @Override
    /**
     * 完成 delete Task 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param taskId task Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param operator operator 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void deleteTask(Long taskId, String operator) {
        SysJobTaskDO task = getRequiredTask(taskId);
        task.setDeleted(1);
        task.setStatus(JobStatusEnum.DISABLED.name());
        task.setUpdateBy(operator);
        task.setUpdateTime(LocalDateTime.now());
        sysJobTaskMapper.updateById(task);
    }

    @Override
    /**
     * 完成 get Required Task 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param taskId task Id 输入值，含义由调用方法名称和所属业务对象限定
     * @return 当前方法计算或转换后的业务结果
     */
    public SysJobTaskDO getRequiredTask(Long taskId) {
        SysJobTaskDO task = sysJobTaskMapper.selectById(taskId);
        if (task == null || task.getDeleted() != null && task.getDeleted() != NOT_DELETED) {
            throw new ServiceException(ApiResultEnum.NOT_FOUND.getCode(), "job task not found");
        }
        return task;
    }

    @Override
    /**
     * 查询 select Due Tasks 所需数据，未命中时按调用场景返回空值或抛出异常。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param triggerTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param limit limit 输入值，含义由调用方法名称和所属业务对象限定
     * @return 解析或查询得到的业务值
     */
    public List<SysJobTaskDO> selectDueTasks(LocalDateTime triggerTime, int limit) {
        return sysJobTaskMapper.selectDueTasks(triggerTime, limit);
    }

    @Override
    /**
     * 完成 try Acquire Lock 分支的校验或转换，返回值供当前调用链继续组装结果。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param task task 输入值，含义由调用方法名称和所属业务对象限定
     * @param nodeId node Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param currentTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @return 当前方法计算或转换后的业务结果
     */
    public boolean tryAcquireLock(SysJobTaskDO task, String nodeId, LocalDateTime currentTime) {
        LocalDateTime lockUntil = currentTime.plusSeconds(Math.max(task.getTimeoutSeconds(), 30));
        return sysJobTaskMapper.acquireLock(task.getId(), nodeId, lockUntil, task.getVersion()) > 0;
    }

    @Override
    /**
     * 推进 mark Scheduled 对应的状态或处理结果，并保留后续查询所需信息。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param taskId task Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param lastTriggerTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     * @param nextTriggerTime 时间值，使用系统约定时区或调用方传入的业务时区解释
     */
    public void markScheduled(Long taskId, LocalDateTime lastTriggerTime, LocalDateTime nextTriggerTime) {
        SysJobTaskDO task = getRequiredTask(taskId);
        task.setLastTriggerTime(lastTriggerTime);
        task.setNextTriggerTime(nextTriggerTime);
        task.setUpdateTime(LocalDateTime.now());
        sysJobTaskMapper.updateById(task);
    }

    @Override
    /**
     * 完成 extend Lock 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param taskId task Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param nodeId node Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param lockUntil lock Until 输入值，含义由调用方法名称和所属业务对象限定
     */
    public void extendLock(Long taskId, String nodeId, LocalDateTime lockUntil) {
        sysJobTaskMapper.extendLock(taskId, nodeId, lockUntil);
    }

    @Override
    /**
     * 完成 finish Task Run 分支的校验或状态更新。
     * <p>
     * 所在层级：当前模块；输入来自调用方传入对象、配置或上游查询结果，输出按方法返回类型或异常边界交付。
     * 涉及状态、金额、密钥、卡数据或远程调用时，需沿用当前调用链的幂等、事务和脱敏约束。
     * </p>
     * @param taskId task Id 输入值，含义由调用方法名称和所属业务对象限定
     * @param lastRunStatus 状态编码，取值必须来自对应枚举或数据库受控字典
     */
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
