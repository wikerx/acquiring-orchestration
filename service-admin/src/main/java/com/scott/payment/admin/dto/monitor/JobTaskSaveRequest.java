package com.scott.payment.admin.dto.monitor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskSaveRequest
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台任务保存请求对象
 * @status : create
 *
 * <p>用于新增或更新任务调度定义，承载调度模式、执行模式、超时重试和任务参数等配置。</p>
 */

@Data
public class JobTaskSaveRequest {

    @NotBlank(message = "jobCode must not be blank")
    private String jobCode;

    @NotBlank(message = "jobName must not be blank")
    private String jobName;

    @NotBlank(message = "jobGroup must not be blank")
    private String jobGroup;

    @NotBlank(message = "handlerCode must not be blank")
    private String handlerCode;

    private String cronExpression;

    @NotBlank(message = "schedulerMode must not be blank")
    private String schedulerMode;

    @NotBlank(message = "triggerMode must not be blank")
    private String triggerMode;

    @NotBlank(message = "misfireStrategy must not be blank")
    private String misfireStrategy;

    @Min(value = 1, message = "timeoutSeconds must be greater than 0")
    @Max(value = 86400, message = "timeoutSeconds must be less than or equal to 86400")
    private Integer timeoutSeconds;

    @Min(value = 0, message = "retryCount must be greater than or equal to 0")
    @Max(value = 10, message = "retryCount must be less than or equal to 10")
    private Integer retryCount;

    @Min(value = 1, message = "retryIntervalSeconds must be greater than 0")
    @Max(value = 86400, message = "retryIntervalSeconds must be less than or equal to 86400")
    private Integer retryIntervalSeconds;

    private Integer allowConcurrent;

    private String params;

    @NotBlank(message = "status must not be blank")
    private String status;

    private String description;
}
