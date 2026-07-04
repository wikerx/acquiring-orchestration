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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskSaveRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 监控治理Job Task Save 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class JobTaskSaveRequest {

    /**
     * 监控治理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @NotBlank(message = "jobCode must not be blank")
    private String jobCode;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @NotBlank(message = "jobName must not be blank")
    private String jobName;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @NotBlank(message = "jobGroup must not be blank")
    private String jobGroup;

    /**
     * 监控治理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    @NotBlank(message = "handlerCode must not be blank")
    private String handlerCode;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String cronExpression;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @NotBlank(message = "schedulerMode must not be blank")
    private String schedulerMode;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @NotBlank(message = "triggerMode must not be blank")
    private String triggerMode;

    /**
     * 监控治理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    @NotBlank(message = "misfireStrategy must not be blank")
    private String misfireStrategy;

    /**
     * 监控治理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    @Min(value = 1, message = "timeoutSeconds must be greater than 0")
    @Max(value = 86400, message = "timeoutSeconds must be less than or equal to 86400")
    private Integer timeoutSeconds;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @Min(value = 0, message = "retryCount must be greater than or equal to 0")
    @Max(value = 10, message = "retryCount must be less than or equal to 10")
    private Integer retryCount;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    @Min(value = 1, message = "retryIntervalSeconds must be greater than 0")
    @Max(value = 86400, message = "retryIntervalSeconds must be less than or equal to 86400")
    private Integer retryIntervalSeconds;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer allowConcurrent;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String params;

    /**
     * 监控治理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    @NotBlank(message = "status must not be blank")
    private String status;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String description;
}
