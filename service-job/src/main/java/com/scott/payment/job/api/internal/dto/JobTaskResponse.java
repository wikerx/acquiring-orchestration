package com.scott.payment.job.api.internal.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskResponse
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务任务响应对象
 * @status : create
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

    private Integer timeoutSeconds;

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
