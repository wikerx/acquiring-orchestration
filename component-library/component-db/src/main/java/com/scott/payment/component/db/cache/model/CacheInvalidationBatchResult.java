package com.scott.payment.component.db.cache.model;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CacheInvalidationBatchResult
 * @date : 2026-08-01 12:00
 * @email : scott_x@163.com
 * @description : 永久缓存失效 Outbox 单批发布结果，为调度指标提供有界数量，不携带商户号或缓存 Key
 * @status : create
 *
 * @param dueCount 本批读取的到期事件数
 * @param successCount 成功或幂等完成的事件数
 */
public record CacheInvalidationBatchResult(int dueCount, int successCount) {
}
