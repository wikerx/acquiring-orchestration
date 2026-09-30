package com.scott.payment.openapi.service.impl;

import com.scott.payment.component.db.reference.model.IpLookupResult;

import java.time.LocalDateTime;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : OpenApiReferenceDataCacheEntry
 * @date : 2026-09-30
 * @email : scott_x@163.com
 * @description : OpenAPI IP 归属专用 Redis 值；业务有效期独立于 Redis 物理 TTL 校验。
 * @status : create
 *
 * @param ipResult IP 查询结果
 * @param validUntil 结果可使用的最晚时刻，不允许为空
 */
public record OpenApiReferenceDataCacheEntry(IpLookupResult ipResult,
                                             LocalDateTime validUntil) {

    /**
     * 判断缓存条目是否仍在 IP 查询的业务有效期内。
     *
     * @param now 当前本地业务时间
     * @return 有效且可复用时返回 true
     */
    public boolean usableAt(LocalDateTime now) {
        return validUntil != null && now.isBefore(validUntil);
    }
}
