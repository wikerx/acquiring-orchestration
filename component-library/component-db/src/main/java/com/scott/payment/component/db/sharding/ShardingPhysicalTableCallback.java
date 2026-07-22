package com.scott.payment.component.db.sharding;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ShardingPhysicalTableCallback
 * @date : 2026-07-21 00:00
 * @email : scott_x@163.com
 * @description : 单物理表分表数据访问回调，位于 component-db 分表基础层，由业务服务使用安全物理表名执行自己的 SQL 或 Mapper 调用。
 * @status : create
 */
@FunctionalInterface
public interface ShardingPhysicalTableCallback<T> {

    /**
     * 使用安全物理表名执行数据访问。
     *
     * @param physicalTable 安全物理表名
     * @return 执行结果
     */
    T execute(String physicalTable);
}
