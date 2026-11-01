package com.scott.payment.component.db.reference.model;

/**
 * 共享 IP 精确查询 JSON 缓存值，以路由版本隔离 IP 数据版本变化
 *
 * @param routingVersion 本地分片路由快照版本
 * @param detail 命中或未命中的完整查询详情，供 OpenAPI 与管理端共同使用
 */
public record IpLookupCacheEntry(String routingVersion, IpLookupDetail detail) {

    /**
     * 从缓存详情读取 OpenAPI 使用的 IP 归属结果。
     *
     * @return 归属结果，缓存详情不完整时返回 null
     */
    public IpLookupResult result() {
        return detail == null ? null : detail.result();
    }
}
