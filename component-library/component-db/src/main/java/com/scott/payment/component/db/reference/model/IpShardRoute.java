package com.scott.payment.component.db.reference.model;

/**
 * IP 路由快照中的不可变查询上下文，把路由版本、物理表和数据版本绑定为同一次解析结果
 *
 * @param routingVersion 完整路由快照的内容摘要
 * @param tableName 已通过路由快照选择的物理表名
 * @param dataVersion 物理表中必须匹配的数据版本
 */
public record IpShardRoute(String routingVersion, String tableName, String dataVersion) {
}
