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
 * 任务触发扫描器
 */
@Component
public class JobTriggerScanner {

    /**
     * 调度启停、扫描周期和批量大小配置。
     */
    private final JobSchedulerProperties jobSchedulerProperties;
    /**
     * 任务定义服务，用于查询到期且启用的任务。
     */
    private final JobTaskService jobTaskService;
    /**
     * 任务分发服务，用于以数据库锁抢占并执行到期任务。
     */
    private final JobDispatchService jobDispatchService;
    /**
     * 当前节点上下文，用于在扫描前检查本地执行容量。
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
