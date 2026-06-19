package com.scott.payment.job.api.internal.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunLogResponse
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务运行日志响应对象
 * @status : create
 */

@Data
public class JobRunLogResponse {

    private Long id;

    private String runId;

    private Long jobId;

    private String jobCode;

    private String jobName;

    private String handlerCode;

    private String triggerType;

    private String schedulerMode;

    private String executeMode;

    private String executorNode;

    private String runStatus;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationMs;

    private Integer retryIndex;

    private Integer maxRetryCount;

    private Integer timeoutSeconds;

    private String paramsSnapshot;

    private String resultMessage;

    private String errorMessage;

    private String traceId;

    private String operatorId;

    private String operatorName;

    private LocalDateTime createTime;
}
