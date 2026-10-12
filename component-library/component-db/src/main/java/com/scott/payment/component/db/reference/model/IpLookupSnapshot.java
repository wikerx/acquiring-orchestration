package com.scott.payment.component.db.reference.model;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : IpLookupSnapshot
 * @date : 2026-10-12
 * @email : scott_x@163.com
 * @description : 单次 IP 数据库查询结果及其实际使用的路由版本，供版本化 Redis 缓存安全回写
 * @status : create
 *
 * @param routingVersion 本次数据库查询实际使用的路由快照版本
 * @param result 命中或未命中的 IP 归属结果
 */
public record IpLookupSnapshot(String routingVersion, IpLookupResult result) {
}
