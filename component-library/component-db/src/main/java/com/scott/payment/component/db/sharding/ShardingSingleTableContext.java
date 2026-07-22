package com.scott.payment.component.db.sharding;

import com.scott.payment.component.db.constant.DataSourceName;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingSingleTableContext
 * @date : 2026-07-21 00:00
 * @email : scott_x@163.com
 * @description : 单物理表分表操作上下文，位于 component-db 分表基础层，用于新增、更新、删除和按分表键单表查询。
 * @status : create
 */
public record ShardingSingleTableContext(String logicalTable, LocalDateTime shardingTime, String dataSource) {

    /**
     * 创建单表操作上下文。
     *
     * @param logicalTable 逻辑表名
     * @param shardingTime 分表时间
     * @param dataSource 数据源名称，读操作可为 slave，写操作通常为 master
     */
    public ShardingSingleTableContext {
        if (dataSource == null || dataSource.isBlank()) {
            dataSource = DataSourceName.MASTER;
        }
    }

    /**
     * 构建单表上下文。
     *
     * @param logicalTable 逻辑表名
     * @param shardingTime 分表时间
     * @return 单表上下文
     */
    public static ShardingSingleTableContext of(String logicalTable, LocalDateTime shardingTime) {
        return new ShardingSingleTableContext(logicalTable, shardingTime, DataSourceName.MASTER);
    }

    /**
     * 构建单表上下文并指定数据源。
     *
     * @param logicalTable 逻辑表名
     * @param shardingTime 分表时间
     * @param dataSource 数据源名称
     * @return 单表上下文
     */
    public static ShardingSingleTableContext of(String logicalTable, LocalDateTime shardingTime, String dataSource) {
        return new ShardingSingleTableContext(logicalTable, shardingTime, dataSource);
    }
}
