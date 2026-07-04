package com.scott.payment.job.api.internal.dto;

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
 * @description : 任务任务保存请求对象
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskSaveRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Task Save 请求对象，位于 service-job 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class JobTaskSaveRequest {

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @NotBlank(message = "jobCode must not be blank")
    private String jobCode;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @NotBlank(message = "jobName must not be blank")
    private String jobName;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @NotBlank(message = "jobGroup must not be blank")
    private String jobGroup;

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @NotBlank(message = "handlerCode must not be blank")
    private String handlerCode;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String cronExpression;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @NotBlank(message = "schedulerMode must not be blank")
    private String schedulerMode;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @NotBlank(message = "triggerMode must not be blank")
    private String triggerMode;

    /**
     * 收单支付金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    @NotBlank(message = "misfireStrategy must not be blank")
    private String misfireStrategy;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    @Min(value = 1, message = "timeoutSeconds must be greater than 0")
    @Max(value = 86400, message = "timeoutSeconds must be less than or equal to 86400")
    private Integer timeoutSeconds;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @Min(value = 0, message = "retryCount must be greater than or equal to 0")
    @Max(value = 10, message = "retryCount must be less than or equal to 10")
    private Integer retryCount;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @Min(value = 1, message = "retryIntervalSeconds must be greater than 0")
    @Max(value = 86400, message = "retryIntervalSeconds must be less than or equal to 86400")
    private Integer retryIntervalSeconds;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer allowConcurrent;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String params;

    /**
     * 收单支付状态字段，取值需与数据字典或枚举约定保持一致。
     */
    @NotBlank(message = "status must not be blank")
    private String status;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String description;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @NotBlank(message = "operator must not be blank")
    private String operator;
}
