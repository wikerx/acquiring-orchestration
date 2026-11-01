package com.scott.payment.component.db.cache.model;

/**
 * 永久缓存失效 Outbox 单批发布结果，为调度指标提供有界数量，不携带商户号或缓存 Key
 *
 * @param dueCount 本批读取的到期事件数
 * @param successCount 成功或幂等完成的事件数
 */
public record CacheInvalidationBatchResult(int dueCount, int successCount) {
}
