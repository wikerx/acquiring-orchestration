package com.scott.payment.job.api.internal.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecutorNodeResponse
 * @date : 2026-06-19 20:30
 * @email : scott_x@163.com
 * @description : 任务执行器节点响应对象
 * @status : create
 */
/**
 * @author : scott
 * @version : v1.0.0
 * @classname : JobExecutorNodeResponse
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 收单支付Job Executor Node 响应对象，位于 service-job 的接口层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class JobExecutorNodeResponse {

    /**
     * 收单支付标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    private Long id;

    /**
     * 收单支付标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    private String nodeId;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String appName;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String host;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer port;

    /**
     * 收单支付标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    private String instanceId;

    /**
     * 收单支付状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private String status;

    /**
     * 收单支付时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private LocalDateTime lastHeartbeatTime;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer currentRunningCount;

    /**
     * 收单支付业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer maxConcurrentCount;
}
