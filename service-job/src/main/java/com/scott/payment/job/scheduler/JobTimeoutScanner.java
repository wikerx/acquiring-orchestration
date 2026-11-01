package com.scott.payment.job.scheduler;

import com.scott.payment.job.config.JobSchedulerProperties;
import com.scott.payment.job.entity.SysJobRunLogDO;
import com.scott.payment.job.executor.JobDispatchService;
import com.scott.payment.job.service.JobRunLogService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 任务超时扫描器
 */
@Component
public class JobTimeoutScanner {

    /**
     * 调度启停、扫描周期和批量大小配置。
     */
    private final JobSchedulerProperties jobSchedulerProperties;
    /**
     * 任务运行日志服务，用于领取已经超过执行期限的运行记录。
     */
    private final JobRunLogService jobRunLogService;
    /**
     * 任务分发服务，用于取消本节点仍在运行的超时 Future。
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
