package com.scott.payment.job.config;

import com.scott.payment.component.job.enums.JobSchedulerModeEnum;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobSchedulerProperties
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务调度配置属性
 * @status : create
 */
@Data
@ConfigurationProperties(prefix = "job.scheduler")
public class JobSchedulerProperties {

    /**
     * 是否启用轻量级调度中心。
     */
    private boolean enabled = true;

    /**
     * 调度模式，默认单机。
     */
    private JobSchedulerModeEnum mode = JobSchedulerModeEnum.STANDALONE;

    /**
     * 任务扫描间隔，单位秒。
     */
    private int scanIntervalSeconds = 5;

    /**
     * 节点心跳间隔，单位秒。
     */
    private int nodeHeartbeatSeconds = 10;

    /**
     * 节点离线判定阈值，单位秒。
     */
    private int nodeOfflineSeconds = 30;

    /**
     * 每次扫描最大处理任务数。
     */
    private int scanBatchSize = 20;

    /**
     * 返回扫描间隔毫秒值，供 @Scheduled 使用。
     *
     * @return 扫描间隔毫秒值
     */
    public long scanIntervalMillis() {
        return Math.max(scanIntervalSeconds, 1) * 1000L;
    }

    /**
     * 返回心跳间隔毫秒值。
     *
     * @return 心跳间隔毫秒值
     */
    public long heartbeatIntervalMillis() {
        return Math.max(nodeHeartbeatSeconds, 1) * 1000L;
    }

    /**
     * 返回离线判定阈值毫秒值。
     *
     * @return 离线判定阈值毫秒值
     */
    public long offlineThresholdMillis() {
        return Math.max(nodeOfflineSeconds, 1) * 1000L;
    }
}
