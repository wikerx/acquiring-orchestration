package com.scott.payment.component.db.cache.service;

import com.scott.payment.component.core.cache.CacheEvictionExecutor;
import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.CacheInvalidationLease;
import com.scott.payment.component.db.cache.entity.ManagedCacheInvalidationOutboxDO;
import com.scott.payment.component.db.cache.mapper.ManagedCacheInvalidationOutboxMapper;
import com.scott.payment.component.db.cache.model.CacheInvalidationBatchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ManagedCacheInvalidationRelayService
 * @date : 2026-08-01 12:00
 * @email : scott_x@163.com
 * @description : Admin 与 Merchant 共用的永久缓存失效中继，按 Outbox 顺序执行精确删除、门禁释放和可重试状态迁移
 * @status : create
 */
@Slf4j
@Service
public class ManagedCacheInvalidationRelayService {

    /** 单次发布失败后的固定重试间隔，单位秒。 */
    private static final long RETRY_DELAY_SECONDS = 5L;

    /** 持久化失败原因的最大字符数。 */
    private static final int FAILURE_REASON_MAX_LENGTH = 512;

    /** 共享 Outbox 数据访问组件。 */
    private final ManagedCacheInvalidationOutboxMapper outboxMapper;

    /** Redis Cache 精确删除契约。 */
    private final CacheEvictionExecutor evictionExecutor;

    /** 删除成功后释放 pending 门禁的共享契约。 */
    private final CacheInvalidationGuard invalidationGuard;

    /**
     * 创建跨服务缓存失效中继。
     *
     * @param outboxMapper Outbox 数据访问组件
     * @param evictionExecutor 精确缓存删除执行器
     * @param invalidationGuard 缓存失效门禁
     */
    public ManagedCacheInvalidationRelayService(
            ManagedCacheInvalidationOutboxMapper outboxMapper,
            CacheEvictionExecutor evictionExecutor,
            CacheInvalidationGuard invalidationGuard) {
        this.outboxMapper = outboxMapper;
        this.evictionExecutor = evictionExecutor;
        this.invalidationGuard = invalidationGuard;
    }

    /**
     * 在独立事务中发布单个失效事件。
     *
     * @param eventId 缓存失效事件唯一标识
     * @return 已发送或本次发送成功时返回 true
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean publish(String eventId) {
        ManagedCacheInvalidationOutboxDO event = outboxMapper.selectByEventId(eventId);
        return event != null && publishEvent(event, LocalDateTime.now());
    }

    /**
     * 在独立事务中按稳定顺序发布一批到期事件。
     *
     * @param limit 单批最大事件数，必须大于零
     * @return 仅包含到期数和成功数的低敏感批次结果
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CacheInvalidationBatchResult publishDueEvents(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("Cache invalidation relay batch size must be positive");
        }
        List<ManagedCacheInvalidationOutboxDO> events =
                outboxMapper.selectDueEvents(LocalDateTime.now(), limit);
        if (events == null || events.isEmpty()) {
            return new CacheInvalidationBatchResult(0, 0);
        }
        int successCount = 0;
        for (ManagedCacheInvalidationOutboxDO event : events) {
            if (publishEvent(event, LocalDateTime.now())) {
                successCount++;
            }
        }
        return new CacheInvalidationBatchResult(events.size(), successCount);
    }

    /**
     * 删除目标缓存、释放门禁并以 CAS 推进 Outbox；任一步失败均保留待重试状态。
     *
     * @param event 待发布的 INIT/FAILED 事件
     * @param now 本次发布尝试时间
     * @return 发布成功、事件已发送或并发方已发送时返回 true
     */
    private boolean publishEvent(ManagedCacheInvalidationOutboxDO event,
                                 LocalDateTime now) {
        if ("SENT".equals(event.getEventStatus())) {
            return true;
        }
        if (!"INIT".equals(event.getEventStatus()) && !"FAILED".equals(event.getEventStatus())) {
            return false;
        }
        int version = event.getVersion() == null ? 0 : event.getVersion();
        try {
            evictionExecutor.evict(event.getCacheName(), event.getBusinessKey());
            invalidationGuard.release(lease(event));
            int marked = outboxMapper.markSent(event.getId(), version, LocalDateTime.now());
            return marked == 1 || alreadySent(event.getEventId());
        } catch (RuntimeException exception) {
            LocalDateTime nextRetryTime = now.plusSeconds(RETRY_DELAY_SECONDS);
            outboxMapper.markFailed(
                    event.getId(),
                    version,
                    nextRetryTime,
                    failureReason(exception),
                    now
            );
            log.warn(
                    "event: MANAGED_CACHE_INVALIDATION_PUBLISH_FAILED "
                            + "eventId: {} cacheName: {} retryCount: {} nextRetryTime: {} reason: {}",
                    event.getEventId(),
                    event.getCacheName(),
                    event.getRetryCount(),
                    nextRetryTime,
                    exception.getMessage()
            );
            return false;
        }
    }

    /**
     * 从持久化事件恢复门禁租约，不输出门禁 token。
     *
     * @param event 缓存失效事件
     * @return 用于校验释放的租约
     */
    private CacheInvalidationLease lease(ManagedCacheInvalidationOutboxDO event) {
        return new CacheInvalidationLease(
                event.getCacheName(),
                event.getBusinessKey(),
                event.getGateToken()
        );
    }

    /**
     * CAS 未更新时复查是否已被并发发布方推进到 SENT。
     *
     * @param eventId 事件唯一标识
     * @return 最新状态为 SENT 时返回 true
     */
    private boolean alreadySent(String eventId) {
        ManagedCacheInvalidationOutboxDO latest = outboxMapper.selectByEventId(eventId);
        return latest != null && "SENT".equals(latest.getEventStatus());
    }

    /**
     * 提取并截断可持久化失败原因，避免异常文本无限增长。
     *
     * @param exception 本次发布异常
     * @return 最多 512 字符的非空原因
     */
    private String failureReason(RuntimeException exception) {
        String reason = exception.getMessage();
        if (!StringUtils.hasText(reason)) {
            reason = exception.getClass().getSimpleName();
        }
        return reason.length() <= FAILURE_REASON_MAX_LENGTH
                ? reason
                : reason.substring(0, FAILURE_REASON_MAX_LENGTH);
    }
}
