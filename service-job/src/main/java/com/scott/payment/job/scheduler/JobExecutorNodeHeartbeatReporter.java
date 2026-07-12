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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecutorNodeHeartbeatReporter
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Executor Node Heartbeat Reporter，位于 service-job 的任务调度层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
public class JobExecutorNodeHeartbeatReporter {

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final JobSchedulerProperties jobSchedulerProperties;
    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
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
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     */
    @Scheduled(fixedDelayString = "#{@jobSchedulerProperties.heartbeatIntervalMillis()}")
    public void markOfflineNodes() {
        if (!jobSchedulerProperties.isEnabled()) {
            return;
        }
        jobExecutorNodeService.markOfflineNodes();
    }
}
