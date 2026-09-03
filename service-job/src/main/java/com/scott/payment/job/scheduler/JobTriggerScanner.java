package com.scott.payment.job.scheduler;

import com.scott.payment.job.config.JobSchedulerProperties;
import com.scott.payment.job.entity.SysJobTaskDO;
import com.scott.payment.job.executor.JobDispatchService;
import com.scott.payment.job.service.JobTaskService;
import com.scott.payment.job.support.JobNodeContext;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTriggerScanner
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务触发扫描器
 * @status : create
 */
@Component
public class JobTriggerScanner {

    /**
     * {@code jobSchedulerProperties}字段，保存 {@code JobTriggerScanner} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * </p>
     */
    private final JobSchedulerProperties jobSchedulerProperties;
    /**
     * {@code jobTaskService} 依赖，用于 {@code JobTriggerScanner} 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * </p>
     */
    private final JobTaskService jobTaskService;
    /**
     * {@code jobDispatchService} 依赖，用于 {@code JobTriggerScanner} 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * </p>
     */
    private final JobDispatchService jobDispatchService;
    /**
     * 任务节点上下文字段，保存 {@code JobTriggerScanner} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    private final JobNodeContext jobNodeContext;

    /**
     * 创建任务触发扫描器。
     *
     * @param jobSchedulerProperties 调度配置
     * @param jobTaskService         任务领域服务
     * @param jobDispatchService     任务分发服务
     * @param jobNodeContext         节点上下文
     */
    public JobTriggerScanner(JobSchedulerProperties jobSchedulerProperties,
                             JobTaskService jobTaskService,
                             JobDispatchService jobDispatchService,
                             JobNodeContext jobNodeContext) {
        this.jobSchedulerProperties = jobSchedulerProperties;
        this.jobTaskService = jobTaskService;
        this.jobDispatchService = jobDispatchService;
        this.jobNodeContext = jobNodeContext;
    }

    /**
     * 周期性扫描到期任务并尝试抢占执行。
     */
    @Scheduled(fixedDelayString = "#{@jobSchedulerProperties.scanIntervalMillis()}")
    public void scanDueTasks() {
        if (!jobSchedulerProperties.isEnabled()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<SysJobTaskDO> dueTasks = jobTaskService.selectDueTasks(now, jobSchedulerProperties.getScanBatchSize());
        for (SysJobTaskDO dueTask : dueTasks) {
            boolean acquired = jobTaskService.tryAcquireLock(dueTask, jobNodeContext.nodeId(), now);
            if (!acquired) {
                continue;
            }
            jobDispatchService.triggerScheduled(dueTask);
        }
    }
}
