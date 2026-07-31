package com.scott.payment.admin.application.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSecurityCacheInvalidationRetryScheduler
 * @date : 2026-07-30 00:00
 * @email : scott_x@163.com
 * @description : 管理端调度层永久缓存失效补偿任务，有界扫描到期 Outbox 并触发独立事务重试
 * @status : create
 *
 * <p>调度配置与类名沿用历史 merchant 命名，避免部署升级时出现两套调度器并发扫描同一表。</p>
 */
@Slf4j
@Component
public class MerchantSecurityCacheInvalidationRetryScheduler {

    /** 单次调度最多处理的到期 Outbox 事件数，避免长事务占用调度线程。 */
    private static final int BATCH_SIZE = 100;

    /** 在独立事务中发布并更新 Outbox 状态的中继服务。 */
    private final MerchantSecurityCacheInvalidationRelayService relayService;

    /**
     * 创建商户安全缓存失效补偿调度器。
     *
     * @param relayService Outbox 事件中继服务
     */
    public MerchantSecurityCacheInvalidationRetryScheduler(
            MerchantSecurityCacheInvalidationRelayService relayService) {
        this.relayService = relayService;
    }

    /**
     * 批量重试已到期的 INIT/FAILED 事件；单条失败由中继服务记录并延后重试。
     */
    @Scheduled(
            initialDelayString = "${payment.merchant.cache-invalidation.relay-initial-delay-ms:5000}",
            fixedDelayString = "${payment.merchant.cache-invalidation.relay-fixed-delay-ms:5000}"
    )
    public void retryDueEvents() {
        int published = relayService.publishDueEvents(BATCH_SIZE);
        if (published > 0) {
            log.info(
                    "event: MERCHANT_SECURITY_CACHE_INVALIDATION_RETRY_COMPLETED publishedCount: {}",
                    published
            );
        }
    }
}
