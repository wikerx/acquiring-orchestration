package com.scott.payment.job.api.internal.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务运行日志响应对象
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

    /**
     * 响应中的调度器模式，用于管理端或商户端展示当前处理结果。
     */
    private String schedulerMode;

    /**
     * 响应中的执行模式，用于管理端或商户端展示当前处理结果。
     */
    private String executeMode;

    /**
     * 响应中的执行器节点，用于管理端或商户端展示当前处理结果。
     */
    private String executorNode;


    private String runStatus;

    /**
     * 响应中的开始时间，用于管理端或商户端展示当前处理结果。
     */
    private LocalDateTime startTime;

    /**
     * 响应中的结束时间，用于管理端或商户端展示当前处理结果。
     */
    private LocalDateTime endTime;

    /**
     * 响应中的{@code durationMs}，用于管理端或商户端展示当前处理结果。
     */
    private Long durationMs;

    /**
     * 响应中的{@code retryIndex}，用于管理端或商户端展示当前处理结果。
     */
    private Integer retryIndex;

    /**
     * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
     */
    private Integer maxRetryCount;

    /**
     * 远程调用超时时间，单位为秒。
     */
    private Integer timeoutSeconds;

    /**
     * 响应中的参数快照，用于管理端或商户端展示当前处理结果。
     */
    private String paramsSnapshot;

    /**
     * 响应中的结果说明，用于管理端或商户端展示当前处理结果。
     */
    private String resultMessage;

    /**
     * 内部错误摘要，用于运营排障；禁止包含密钥、卡数据和完整报文。
     */
    private String errorMessage;


    private String traceId;

    /**
     * 执行本次管理操作的可信登录账号 ID，用于操作审计。
     */
    private String operatorId;

    /**
     * 执行本次管理操作时的账号显示名称快照，用于操作审计。
     */
    private String operatorName;

    /**
     * 记录创建时刻，持久化精度为毫秒。
     */
    private LocalDateTime createTime;
}
