package com.scott.payment.component.db.reference.model;

/**
 * 单次 IP 数据库查询结果及其实际使用的路由版本，供版本化 Redis 缓存安全回写
 *
 * @param routingVersion 本次数据库查询实际使用的路由快照版本
 * @param detail 命中或未命中的完整查询详情
 */
public record IpLookupSnapshot(String routingVersion, IpLookupDetail detail) {

    /**
     * 从本次数据库查询详情读取 OpenAPI 使用的 IP 归属结果。
     *
     * @return 归属结果，查询详情不完整时返回 null
     */
    public IpLookupResult result() {
        return detail == null ? null : detail.result();
    }
}
