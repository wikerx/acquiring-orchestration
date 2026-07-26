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

    /**
     * job Scheduler Properties，用于保存 Job Executor Node Heartbeat Reporter 中与 jobschedulerproperties 相关的业务属性。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
     */
    private final JobSchedulerProperties jobSchedulerProperties;
    /**
     * job Executor Node Service 依赖，用于 Job Executor Node Heartbeat Reporter 调用对应的数据访问、远程调用或领域服务能力。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；是否允许为空由接口校验、数据库约束或调用契约决定；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：Spring 容器构造器注入。
     * 字段关系：与同记录的主键、业务编号、状态和审计时间一起用于查询、展示或排障。
     * </p>
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
