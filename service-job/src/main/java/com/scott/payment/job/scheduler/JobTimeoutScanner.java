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
     * {@code jobSchedulerProperties}字段，保存 {@code JobTimeoutScanner} 当前处理所需的业务取值。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * </p>
     */
    private final JobSchedulerProperties jobSchedulerProperties;
    /**
     * {@code jobRunLogService} 依赖，用于 {@code JobTimeoutScanner} 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * </p>
     */
    private final JobRunLogService jobRunLogService;
    /**
     * {@code jobDispatchService} 依赖，用于 {@code JobTimeoutScanner} 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
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
