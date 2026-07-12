package com.scott.payment.admin.dto.monitor;

import lombok.Data;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingTablePreCreateTableResultResponse
 * @date : 2026-07-04 16:30
 * @email : scott_x@163.com
 * @description : 监控治理Sharding Table Pre Create Table Result 响应对象，位于 service-admin 的接口传输层，用于承载该模块对应的业务职责和数据流转边界。
 * @status : create
 */
@Data
public class ShardingTablePreCreateTableResultResponse {

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
    private String targetQuarter;

    /**
     * 监控治理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private String status;

    /**
     * 监控治理状态字段，取值需与数据字典或枚举约定保持一致。
     */
    private String schemaCheckStatus;

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
     * 监控治理业务字段，承载页面展示、接口传输或持久化所需的数据语义。
     */
    private String message;
}
