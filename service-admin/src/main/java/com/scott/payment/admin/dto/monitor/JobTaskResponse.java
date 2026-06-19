package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskResponse
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务定义响应 DTO
 * @status : create
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
