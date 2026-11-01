package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台任务定义响应 DTO
 *
 * <p>用于任务配置列表与详情展示，承载任务调度配置、重试配置和最近运行状态摘要。</p>
 */
@Data
public class JobTaskResponse {

    private Long id;

    private String jobCode;

    private String jobName;

    private String jobGroup;

    private String handlerCode;

    private String cronExpression;

    private String schedulerMode;

    private String triggerMode;

    private String executeMode;

    private String routeStrategy;

    private String misfireStrategy;

    /**
     * 远程调用超时时间，单位为秒。
     */
    private Integer timeoutSeconds;

    /**
     * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
     */
    private Integer retryCount;

    private Integer retryIntervalSeconds;

    private Integer allowConcurrent;

    private String params;

    private String status;

    private String description;

    private LocalDateTime nextTriggerTime;

    private LocalDateTime lastTriggerTime;

    private String lastRunStatus;
}
