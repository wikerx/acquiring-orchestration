package com.scott.payment.job.scheduler;

import com.scott.payment.job.config.JobSchedulerProperties;
import com.scott.payment.job.entity.SysJobRunLogDO;
import com.scott.payment.job.executor.JobDispatchService;
import com.scott.payment.job.service.JobRunLogService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTimeoutScanner
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务超时扫描器
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTimeoutScanner
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Timeout Scanner，位于 service-job 的任务调度层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Component
public class JobTimeoutScanner {

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final JobSchedulerProperties jobSchedulerProperties;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final JobRunLogService jobRunLogService;
    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private final JobDispatchService jobDispatchService;

    /**
     * 创建任务超时扫描器。
     *
     * @param jobSchedulerProperties 调度配置
     * @param jobRunLogService       执行日志服务
     * @param jobDispatchService     任务分发服务
     */
    public JobTimeoutScanner(JobSchedulerProperties jobSchedulerProperties,
                             JobRunLogService jobRunLogService,
                             JobDispatchService jobDispatchService) {
        this.jobSchedulerProperties = jobSchedulerProperties;
        this.jobRunLogService = jobRunLogService;
        this.jobDispatchService = jobDispatchService;
    }

    /**
     * 周期性扫描超时任务并标记 TIMEOUT。
     */
    /**
     * 执行收单支付相关处理，保持当前层级的职责边界和返回语义。
     */
    @Scheduled(fixedDelayString = "#{@jobSchedulerProperties.scanIntervalMillis()}")
    public void scanTimeoutTasks() {
        if (!jobSchedulerProperties.isEnabled()) {
            return;
        }
        List<SysJobRunLogDO> timeoutLogs = jobRunLogService.selectTimeoutCandidates();
        for (SysJobRunLogDO timeoutLog : timeoutLogs) {
            jobDispatchService.markTimeout(timeoutLog);
        }
    }
}
