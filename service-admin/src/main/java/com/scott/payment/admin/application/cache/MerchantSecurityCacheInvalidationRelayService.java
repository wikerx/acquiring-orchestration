package com.scott.payment.admin.application.cache;

import com.scott.payment.admin.entity.MerchantSecurityCacheInvalidationOutboxDO;
import com.scott.payment.admin.mapper.MerchantSecurityCacheInvalidationOutboxMapper;
import com.scott.payment.admin.observability.CacheInvalidationOutboxMetrics;
import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.CacheInvalidationLease;
import com.scott.payment.component.redis.cache.invalidation.ImmediateCacheEvictionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSecurityCacheInvalidationRelayService
 * @date : 2026-07-30 00:00
 * @email : scott_x@163.com
 * @description : 管理端永久缓存失效中继服务，按 Outbox 事件顺序执行精确删除、token 门禁释放与持久重试
 * @status : create
 *
 * <p>类名保留历史命名以兼容既有 Outbox 表和监控标签，实际事件由 cacheName 区分商户资料、
 * OpenAPI 策略与平台公开配置。</p>
 */
@Slf4j
@Service
public class MerchantSecurityCacheInvalidationRelayService {

    /** 单次发布失败后的固定重试间隔，单位秒。 */
    private static final long RETRY_DELAY_SECONDS = 5L;

    /** Outbox 失败原因允许持久化的最大字符数。 */
    private static final int FAILURE_REASON_MAX_LENGTH = 512;

    /** 查询并以乐观锁更新商户安全缓存失效事件。 */
    private final MerchantSecurityCacheInvalidationOutboxMapper outboxMapper;

    /** 按缓存名称和商户业务键立即删除本地/远端缓存。 */
    private final ImmediateCacheEvictionService evictionService;

    /** 缓存删除成功后释放防旧值回填门闩。 */
    private final CacheInvalidationGuard invalidationGuard;

    /** 缓存失效 Outbox 低基数指标记录器。 */
    private final CacheInvalidationOutboxMetrics metrics;

    /**
     * 创建商户安全缓存失效 Outbox 中继服务。
     *
     * @param outboxMapper Outbox 数据访问组件
     * @param evictionService 即时缓存删除服务
     * @param invalidationGuard 缓存失效门闩服务
     * @param metrics 缓存失效 Outbox 指标记录器
     */
    @Autowired
    public MerchantSecurityCacheInvalidationRelayService(
            MerchantSecurityCacheInvalidationOutboxMapper outboxMapper,
            ImmediateCacheEvictionService evictionService,
            CacheInvalidationGuard invalidationGuard,
            CacheInvalidationOutboxMetrics metrics) {
        this.outboxMapper = outboxMapper;
        this.evictionService = evictionService;
        this.invalidationGuard = invalidationGuard;
        this.metrics = metrics;
    }

    /**
     * 创建不产生指标副作用的缓存失效中继服务，供纯单元测试直接构造。
     *
     * @param outboxMapper Outbox 数据访问组件
     * @param evictionService 即时缓存删除服务
     * @param invalidationGuard 缓存失效门闩服务
     */
    public MerchantSecurityCacheInvalidationRelayService(
            MerchantSecurityCacheInvalidationOutboxMapper outboxMapper,
            ImmediateCacheEvictionService evictionService,
            CacheInvalidationGuard invalidationGuard) {
        this(
                outboxMapper,
                evictionService,
                invalidationGuard,
                CacheInvalidationOutboxMetrics.noop()
        );
    }

    /**
     * 在独立事务中按事件号发布单个失效事件。
     *
     * @param eventId 缓存失效事件唯一标识
     * @return 事件已发送或本次发送成功时返回 {@code true}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean publish(String eventId) {
        MerchantSecurityCacheInvalidationOutboxDO event = outboxMapper.selectByEventId(eventId);
        return event != null && publishEvent(event, LocalDateTime.now());
    }

    /**
     * 在独立事务中按稳定顺序发布一批到期事件。
     *
     * @param limit 单批最大事件数
     * @return 本批已成功或幂等完成的事件数量
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int publishDueEvents(int limit) {
        long startNanos = System.nanoTime();
        try {
            List<MerchantSecurityCacheInvalidationOutboxDO> events =
                    outboxMapper.selectDueEvents(LocalDateTime.now(), limit);
            if (events == null || events.isEmpty()) {
                metrics.recordBatch(
                        CacheInvalidationOutboxMetrics.Outbox.MERCHANT_SECURITY,
                        0,
                        limit,
                        0,
                        System.nanoTime() - startNanos
                );
                return 0;
            }
            int successCount = 0;
            for (MerchantSecurityCacheInvalidationOutboxDO event : events) {
                if (publishEvent(event, LocalDateTime.now())) {
                    successCount++;
                }
            }
            metrics.recordBatch(
                    CacheInvalidationOutboxMetrics.Outbox.MERCHANT_SECURITY,
                    events.size(),
                    limit,
                    successCount,
                    System.nanoTime() - startNanos
            );
            return successCount;
        } catch (RuntimeException exception) {
            metrics.recordBatchError(
                    CacheInvalidationOutboxMetrics.Outbox.MERCHANT_SECURITY,
                    System.nanoTime() - startNanos
            );
            throw exception;
        }
    }

    /**
     * 删除目标缓存、释放门闩并以 CAS 推进 Outbox；任一步失败均记录待重试状态。
     *
     * @param event 待发布的 INIT/FAILED 事件
     * @param now 本次发布尝试时间
     * @return 发布成功、事件已发送或并发方已发送时返回 {@code true}
     */
    private boolean publishEvent(MerchantSecurityCacheInvalidationOutboxDO event,
                                 LocalDateTime now) {
        if ("SENT".equals(event.getEventStatus())) {
            return true;
        }
        if (!"INIT".equals(event.getEventStatus()) && !"FAILED".equals(event.getEventStatus())) {
            return false;
        }
        int version = event.getVersion() == null ? 0 : event.getVersion();
        try {
            evictionService.evict(event.getCacheName(), event.getBusinessKey());
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
                    "event: MERCHANT_SECURITY_CACHE_INVALIDATION_PUBLISH_FAILED "
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
     * 从持久化事件恢复缓存失效租约，不在日志中暴露门闩 token。
     *
     * @param event 缓存失效事件
     * @return 可供失效门闩服务校验释放的租约
     */
    private CacheInvalidationLease lease(MerchantSecurityCacheInvalidationOutboxDO event) {
        return new CacheInvalidationLease(
                event.getCacheName(),
                event.getBusinessKey(),
                event.getGateToken()
        );
    }

    /**
     * CAS 未更新时复查事件是否已被并发发布方推进到 SENT。
     *
     * @param eventId 缓存失效事件唯一标识
     * @return 最新状态为 SENT 时返回 {@code true}
     */
    private boolean alreadySent(String eventId) {
        MerchantSecurityCacheInvalidationOutboxDO latest = outboxMapper.selectByEventId(eventId);
        return latest != null && "SENT".equals(latest.getEventStatus());
    }

    /**
     * 提取并截断可持久化失败原因，避免异常文本无限增长。
     *
     * @param exception 本次缓存失效异常
     * @return 最多 512 个字符的非空失败原因
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
