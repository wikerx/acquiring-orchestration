package com.scott.payment.admin.application.risk.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskCacheInvalidationRetryScheduler
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 定时补偿未成功发布的风控规则缓存失效事件。
 * @status : create
 */
@Slf4j
@Component
public class RiskCacheInvalidationRetryScheduler {

    /** 单次调度最多处理的到期 Outbox 事件数。 */
    private static final int BATCH_SIZE = 100;

    /** 在独立事务中提交 generation 并更新 Outbox 状态的中继服务。 */
    private final RiskCacheInvalidationRelayService relayService;

    /**
     * 创建风控规则缓存失效补偿调度器。
     *
     * @param relayService generation 发布中继服务
     */
    public RiskCacheInvalidationRetryScheduler(RiskCacheInvalidationRelayService relayService) {
        this.relayService = relayService;
    }

    /**
     * 扫描并重试到期失效事件。
     */
    @Scheduled(
            initialDelayString = "${payment.risk.cache-invalidation.relay-initial-delay-ms:5000}",
            fixedDelayString = "${payment.risk.cache-invalidation.relay-fixed-delay-ms:5000}"
    )
    public void retryDueEvents() {
        int published = relayService.publishDueEvents(BATCH_SIZE);
        if (published > 0) {
            log.info("event: RISK_CACHE_INVALIDATION_RETRY_COMPLETED publishedCount: {}", published);
        }
    }
}
