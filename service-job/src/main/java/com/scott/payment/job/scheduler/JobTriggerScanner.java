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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTriggerScanner
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Trigger Scanner，位于 service-job 的任务调度层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
public class JobTriggerScanner {

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final JobSchedulerProperties jobSchedulerProperties;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final JobTaskService jobTaskService;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final JobDispatchService jobDispatchService;
    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
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
