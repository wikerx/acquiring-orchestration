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
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskResponse
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 监控治理Job Task 响应对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class JobTaskResponse {

    /**
     * 监控治理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    private Long id;

    /**
     * 监控治理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private String jobCode;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String jobName;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String jobGroup;

    /**
     * 监控治理编码或编号字段，用于业务识别、查询和幂等关联。
     */
    private String handlerCode;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String cronExpression;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String schedulerMode;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String triggerMode;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String executeMode;

    /**
     * 监控治理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    private String routeStrategy;

    /**
     * 监控治理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    private String misfireStrategy;

    /**
     * 监控治理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private Integer timeoutSeconds;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer retryCount;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
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
    private String status;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String description;

    /**
     * 监控治理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private LocalDateTime nextTriggerTime;

    /**
     * 监控治理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private LocalDateTime lastTriggerTime;

    /**
     * 监控治理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private String lastRunStatus;
}
