package com.scott.payment.component.db.sharding;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingPhysicalTablesCallback
 * @date : 2026-07-21 00:00
 * @email : scott_x@163.com
 * @description : 多物理表分表数据访问回调，位于 component-db 分表基础层，由业务服务按统一解析后的表序执行跨表查询。
 * @status : create
 */
@FunctionalInterface
public interface ShardingPhysicalTablesCallback<T> {

    /**
     * 使用安全物理表名列表执行数据访问。
     *
     * @param physicalTables 安全物理表名列表，通常按季度倒序排列
     * @return 执行结果
     */
    T execute(List<String> physicalTables);
}
