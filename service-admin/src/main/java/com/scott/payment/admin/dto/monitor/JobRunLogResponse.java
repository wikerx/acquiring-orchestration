package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 管理后台任务运行日志响应 DTO
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

    /**
     * 触发类型，用于区分手工触发、定时调度、失败重试或系统补偿执行。
     */
    private String triggerType;

    private String schedulerMode;

    private String executeMode;

    private String executorNode;

    private String runStatus;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationMs;

    private Integer retryIndex;

    /**
     * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
     */
    private Integer maxRetryCount;

    /**
     * 远程调用超时时间，单位为秒。
     */
    private Integer timeoutSeconds;

    private String paramsSnapshot;

    private String resultMessage;

    /**
     * 内部错误摘要，用于运营排障；禁止包含密钥、卡数据和完整报文。
     */
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
