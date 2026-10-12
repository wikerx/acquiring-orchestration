package com.scott.payment.component.db.reference.model;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLookupCacheEntry
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : 共享 IP 精确查询缓存值，以路由版本隔离 IP 数据版本变化
 * @status : create
 *
 * @param routingVersion 本地分片路由快照版本
 * @param result 命中或未命中的完整查询结果
 */
public record IpLookupCacheEntry(String routingVersion, IpLookupResult result) {
}
