package com.scott.payment.job.api.internal.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 调度任务详情响应。
 */
@Data
public class JobTaskResponse {


    private Long id;


    private String jobCode;


    private String jobName;

    /**
     * 响应中的任务分组，用于管理端或商户端展示当前处理结果。
     */
    private String jobGroup;


    private String handlerCode;

    /**
     * 响应中的{@code cronExpression}，用于管理端或商户端展示当前处理结果。
     */
    private String cronExpression;

    /**
     * 响应中的调度器模式，用于管理端或商户端展示当前处理结果。
     */
    private String schedulerMode;

    /**
     * 响应中的触发模式，用于管理端或商户端展示当前处理结果。
     */
    private String triggerMode;

    /**
     * 响应中的执行模式，用于管理端或商户端展示当前处理结果。
     */
    private String executeMode;

    /**
     * 响应中的{@code routeStrategy}，用于管理端或商户端展示当前处理结果。
     */
    private String routeStrategy;

    /**
     * 响应中的{@code misfireStrategy}，用于管理端或商户端展示当前处理结果。
     */
    private String misfireStrategy;

    /**
     * 远程调用超时时间，单位为秒。
     */
    private Integer timeoutSeconds;

    /**
     * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
     */
    private Integer retryCount;

    /**
     * 响应中的{@code retryIntervalSeconds}，用于管理端或商户端展示当前处理结果。
     */
    private Integer retryIntervalSeconds;

    /**
     * 响应中的{@code allowConcurrent}，用于管理端或商户端展示当前处理结果。
     */
    private Integer allowConcurrent;

    /**
     * 响应中的参数，用于管理端或商户端展示当前处理结果。
     */
    private String params;


    private String status;


    private String description;

    /**
     * 响应中的{@code nextTriggerTime}，用于管理端或商户端展示当前处理结果。
     */
    private LocalDateTime nextTriggerTime;

    /**
     * 响应中的{@code lastTriggerTime}，用于管理端或商户端展示当前处理结果。
     */
    private LocalDateTime lastTriggerTime;


    private String lastRunStatus;
}
