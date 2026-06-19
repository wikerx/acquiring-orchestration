package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobRunLogResponse
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务运行日志响应 DTO
 * @status : create
 *
 * <p>用于任务运行日志列表与详情展示，承载一次执行的调度、重试、耗时和结果摘要信息。</p>
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

    /**
     * 操作人 ID，可为空。
     */
    private String operatorId;

    /**
     * 操作人名称，可为空。
     */
    private String operatorName;

    /**
     * 日志创建时间。
     */
    private LocalDateTime createTime;
}
