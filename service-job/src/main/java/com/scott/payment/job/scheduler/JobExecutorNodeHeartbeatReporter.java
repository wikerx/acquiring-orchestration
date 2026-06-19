package com.scott.payment.job.scheduler;

import com.scott.payment.job.config.JobSchedulerProperties;
import com.scott.payment.job.service.JobExecutorNodeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecutorNodeHeartbeatReporter
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务执行器节点心跳上报器
 * @status : create
 */

@Component
public class JobExecutorNodeHeartbeatReporter {

    private final JobSchedulerProperties jobSchedulerProperties;
    private final JobExecutorNodeService jobExecutorNodeService;

    /**
     * 创建节点心跳扫描器。
     *
     * @param jobSchedulerProperties 调度配置
     * @param jobExecutorNodeService 节点领域服务
     */
    public JobExecutorNodeHeartbeatReporter(JobSchedulerProperties jobSchedulerProperties,
                                            JobExecutorNodeService jobExecutorNodeService) {
        this.jobSchedulerProperties = jobSchedulerProperties;
        this.jobExecutorNodeService = jobExecutorNodeService;
    }

    /**
     * 周期性上报当前节点心跳。
     */
    @Scheduled(fixedDelayString = "#{@jobSchedulerProperties.heartbeatIntervalMillis()}")
    public void reportHeartbeat() {
        if (!jobSchedulerProperties.isEnabled()) {
            return;
        }
        jobExecutorNodeService.reportHeartbeat();
    }

    /**
     * 周期性将超时节点标记为离线。
     */
    @Scheduled(fixedDelayString = "#{@jobSchedulerProperties.heartbeatIntervalMillis()}")
    public void markOfflineNodes() {
        if (!jobSchedulerProperties.isEnabled()) {
            return;
        }
        jobExecutorNodeService.markOfflineNodes();
    }
}
