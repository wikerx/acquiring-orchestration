package com.scott.payment.component.db.sharding;

import com.scott.payment.component.db.constant.DataSourceName;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingRangeTableContext
 * @date : 2026-07-21 00:00
 * @email : scott_x@163.com
 * @description : 范围分表查询上下文，位于 component-db 分表基础层，用于按时间范围统一解析多个物理表。
 * @status : create
 */
public record ShardingRangeTableContext(String logicalTable, LocalDateTime beginTime, LocalDateTime endTime, String dataSource) {

    /**
     * 创建范围查询上下文。
     *
     * @param logicalTable 逻辑表名
     * @param beginTime 查询开始时间
     * @param endTime 查询结束时间
     * @param dataSource 数据源名称，范围查询默认走 slave
     */
    public ShardingRangeTableContext {
        if (dataSource == null || dataSource.isBlank()) {
            dataSource = DataSourceName.SLAVE;
        }
    }

    /**
     * 构建默认从库范围查询上下文。
     *
     * @param logicalTable 逻辑表名
     * @param beginTime 查询开始时间
     * @param endTime 查询结束时间
     * @return 范围查询上下文
     */
    public static ShardingRangeTableContext of(String logicalTable, LocalDateTime beginTime, LocalDateTime endTime) {
        return new ShardingRangeTableContext(logicalTable, beginTime, endTime, DataSourceName.SLAVE);
    }

    /**
     * 构建范围查询上下文并指定数据源。
     *
     * @param logicalTable 逻辑表名
     * @param beginTime 查询开始时间
     * @param endTime 查询结束时间
     * @param dataSource 数据源名称
     * @return 范围查询上下文
     */
    public static ShardingRangeTableContext of(String logicalTable, LocalDateTime beginTime, LocalDateTime endTime, String dataSource) {
        return new ShardingRangeTableContext(logicalTable, beginTime, endTime, dataSource);
    }
}
