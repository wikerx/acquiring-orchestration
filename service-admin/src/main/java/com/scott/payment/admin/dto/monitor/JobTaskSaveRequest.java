package com.scott.payment.admin.dto.monitor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 管理后台任务保存请求对象
 *
 * <p>用于新增或更新任务调度定义，承载调度模式、执行模式、超时重试和任务参数等配置。</p>
 */
@Data
public class JobTaskSaveRequest {

    @NotBlank(message = "jobCode must not be blank")
    private String jobCode;

    @NotBlank(message = "jobName must not be blank")
    private String jobName;

    /**
     * 请求中的任务分组，用于限定本次操作的输入和校验范围。
     */
    @NotBlank(message = "jobGroup must not be blank")
    private String jobGroup;

    @NotBlank(message = "handlerCode must not be blank")
    private String handlerCode;

    private String cronExpression;

    /**
     * 请求中的调度器模式，用于限定本次操作的输入和校验范围。
     */
    @NotBlank(message = "schedulerMode must not be blank")
    private String schedulerMode;

    /**
     * 请求中的触发模式，用于限定本次操作的输入和校验范围。
     */
    @NotBlank(message = "triggerMode must not be blank")
    private String triggerMode;

    @NotBlank(message = "misfireStrategy must not be blank")
    private String misfireStrategy;

    /**
     * 远程调用超时时间，单位为秒。
     */
    @Min(value = 1, message = "timeoutSeconds must be greater than 0")
    @Max(value = 86400, message = "timeoutSeconds must be less than or equal to 86400")
    private Integer timeoutSeconds;

    /**
     * 重试次数，用于记录 MQ、任务或商户通知当前已执行的重试轮次。
     */
    @Min(value = 0, message = "retryCount must be greater than or equal to 0")
    @Max(value = 10, message = "retryCount must be less than or equal to 10")
    private Integer retryCount;

    @Min(value = 1, message = "retryIntervalSeconds must be greater than 0")
    @Max(value = 86400, message = "retryIntervalSeconds must be less than or equal to 86400")
    private Integer retryIntervalSeconds;

    private Integer allowConcurrent;

    /**
     * 请求中的参数，用于限定本次操作的输入和校验范围。
     */
    private String params;

    @NotBlank(message = "status must not be blank")
    private String status;

    private String description;
}
