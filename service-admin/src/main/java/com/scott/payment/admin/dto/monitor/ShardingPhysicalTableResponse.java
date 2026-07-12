package com.scott.payment.admin.dto.monitor;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingPhysicalTableResponse
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 监控治理Sharding Physical Table 响应对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class ShardingPhysicalTableResponse {

    /**
     * 监控治理标识字段，用于关联数据库记录或业务主体，不能为空时由请求校验或数据库约束保证。
     */
    private Long id;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String logicalTable;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String templateTable;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String physicalTable;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String shardingColumn;

    /**
     * 监控治理金额、费率或数值字段，需保持精度语义，禁止使用浮点数替代。
     */
    private String strategy;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer year;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer quarter;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String quarterSuffix;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String dataSource;

    /**
     * 监控治理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private String tableStatus;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Integer autoCreated;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Long autoIncrementStart;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Long autoIncrementCurrent;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private Long autoIncrementMax;

    /**
     * 监控治理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private String schemaCheckStatus;

    /**
     * 监控治理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private LocalDateTime lastCheckTime;

    /**
     * 监控治理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private LocalDateTime createdTime;

    /**
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String errorMessage;

    /**
     * 监控治理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private LocalDateTime createTime;

    /**
     * 监控治理时间字段，表示具体时刻时使用 LocalDateTime 并由页面统一格式化展示。
     */
    private LocalDateTime updateTime;
}
