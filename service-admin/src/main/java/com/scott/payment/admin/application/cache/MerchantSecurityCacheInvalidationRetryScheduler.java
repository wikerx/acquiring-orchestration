package com.scott.payment.admin.application.cache;

import com.scott.payment.admin.observability.CacheInvalidationOutboxMetrics;
import com.scott.payment.component.db.cache.model.CacheInvalidationBatchResult;
import com.scott.payment.component.db.cache.service.ManagedCacheInvalidationRelayService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 管理端调度层永久缓存失效补偿任务，有界扫描到期 Outbox 并触发独立事务重试
 *
 * <p>调度配置与类名沿用历史 merchant 命名，避免部署升级时出现两套调度器并发扫描同一表。</p>
 */
@Slf4j
@Component
public class MerchantSecurityCacheInvalidationRetryScheduler {

    /**
     * 单次调度最多处理的到期 Outbox 事件数，避免长事务占用调度线程。
     */
    private static final int BATCH_SIZE = 100;

    /**
     * 在独立事务中发布并更新 Outbox 状态的中继服务。
     */
    private final ManagedCacheInvalidationRelayService relayService;

    /**
     * Admin 侧统一记录补偿批次、饱和和发布结果。
     */
    private final CacheInvalidationOutboxMetrics metrics;

    /**
     * 创建商户安全缓存失效补偿调度器。
     *
     * @param relayService Outbox 事件中继服务
     */
    public MerchantSecurityCacheInvalidationRetryScheduler(
            ManagedCacheInvalidationRelayService relayService,
            CacheInvalidationOutboxMetrics metrics) {
        this.relayService = relayService;
        this.metrics = metrics;
    }

    /**
     * 批量重试已到期的 INIT/FAILED 事件；单条失败由中继服务记录并延后重试。
     */
    @Scheduled(
            initialDelayString = "${payment.merchant.cache-invalidation.relay-initial-delay-ms:5000}",
            fixedDelayString = "${payment.merchant.cache-invalidation.relay-fixed-delay-ms:5000}"
    )
    public void retryDueEvents() {
        long startNanos = System.nanoTime();
        try {
            CacheInvalidationBatchResult result = relayService.publishDueEvents(BATCH_SIZE);
            metrics.recordBatch(
                    CacheInvalidationOutboxMetrics.Outbox.MERCHANT_SECURITY,
                    result.dueCount(),
                    BATCH_SIZE,
                    result.successCount(),
                    System.nanoTime() - startNanos
            );
            if (result.successCount() > 0) {
                log.info(
                        "商户安全缓存失效事件重试完成, event: MERCHANT_SECURITY_CACHE_INVALIDATION_RETRY_COMPLETED, publishedCount: {}",
                        result.successCount()
                );
            }
        } catch (RuntimeException exception) {
            metrics.recordBatchError(
                    CacheInvalidationOutboxMetrics.Outbox.MERCHANT_SECURITY,
                    System.nanoTime() - startNanos
            );
            throw exception;
        }
    }
}
