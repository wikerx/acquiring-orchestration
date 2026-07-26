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
     * job Scheduler Properties 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final JobSchedulerProperties jobSchedulerProperties;
    /**
     * job Task Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final JobTaskService jobTaskService;
    /**
     * job Dispatch Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final JobDispatchService jobDispatchService;
    /**
     * job Node Context 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
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
