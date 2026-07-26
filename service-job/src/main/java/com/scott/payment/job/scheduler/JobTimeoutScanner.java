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
@Component
public class JobTimeoutScanner {

    /**
     * job Scheduler Properties 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final JobSchedulerProperties jobSchedulerProperties;
    /**
     * job Run Log Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
     */
    private final JobRunLogService jobRunLogService;
    /**
     * job Dispatch Service 字段，表示当前模型在所属业务流程中的对应属性。
     * <p>
     * 单位：无；格式：由上游接口、数据库字段或枚举定义约束；是否允许为空由数据库约束、校验注解或调用契约决定；非敏感字段，仍需按最小必要原则使用。
     * 数据来源：接口请求、数据库记录、配置文件或上游服务返回；与同对象字段共同组成当前业务语义。
     * </p>
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
