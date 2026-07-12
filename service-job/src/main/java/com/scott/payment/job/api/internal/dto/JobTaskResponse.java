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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskResponse
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Task 响应对象，位于 service-job 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class JobTaskResponse {

    /**
     * 收单支付标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    private Long id;

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private String jobCode;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String jobName;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String jobGroup;

    /**
     * 收单支付编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private String handlerCode;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String cronExpression;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String schedulerMode;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String triggerMode;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String executeMode;

    /**
     * 收单支付金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    private String routeStrategy;

    /**
     * 收单支付金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    private String misfireStrategy;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private Integer timeoutSeconds;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer retryCount;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
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
    private String status;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String description;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private LocalDateTime nextTriggerTime;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private LocalDateTime lastTriggerTime;

    /**
     * 收单支付状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private String lastRunStatus;
}
