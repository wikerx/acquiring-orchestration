package com.scott.payment.admin.client.job.dto;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskRemoteSaveRequest
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 管理后台调用调度中心时使用的任务保存请求对象
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobTaskRemoteSaveRequest
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Task Remote Save 请求对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class JobTaskRemoteSaveRequest {

    /**
     * 任务编码。
     */
    private String jobCode;

    /**
     * 任务名称。
     */
    private String jobName;

    /**
     * 任务分组。
     */
    private String jobGroup;

    /**
     * 处理器编码。
     */
    private String handlerCode;

    /**
     * Cron 表达式。
     */
    private String cronExpression;

    /**
     * 调度模式。
     */
    private String schedulerMode;

    /**
     * 触发模式。
     */
    private String triggerMode;

    /**
     * 错过触发处理策略。
     */
    private String misfireStrategy;

    /**
     * 超时时间，单位秒。
     */
    private Integer timeoutSeconds;

    /**
     * 重试次数。
     */
    private Integer retryCount;

    /**
     * 重试间隔，单位秒。
     */
    private Integer retryIntervalSeconds;

    /**
     * 是否允许并发执行。
     */
    private Integer allowConcurrent;

    /**
     * 任务参数。
     */
    private String params;

    /**
     * 任务状态。
     */
    private String status;

    /**
     * 任务描述。
     */
    private String description;

    /**
     * 当前操作人。
     */
    private String operator;
}
