package com.scott.payment.component.db.cache.service;

import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.CacheInvalidationLease;
import com.scott.payment.component.db.cache.entity.ManagedCacheInvalidationOutboxDO;
import com.scott.payment.component.db.cache.mapper.ManagedCacheInvalidationOutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : ManagedCacheInvalidationCoordinator
 * @date : 2026-08-01 12:00
 * @email : scott_x@163.com
 * @description : 平台写服务共用的事务缓存失效协调器，将 pending 门禁和 Outbox 意图绑定到同一数据库事务
 * @status : create
 */
@Slf4j
@Service
public class ManagedCacheInvalidationCoordinator {

    /** 当前数据库事务绑定事件集合时使用的实例级资源键。 */
    private final Object transactionResourceKey = new Object();

    /** 在数据库提交完成前阻止旧缓存重新写回的门禁。 */
    private final CacheInvalidationGuard invalidationGuard;

    /** pending 门禁异常遗留的恢复性 TTL，不替代 Outbox 重试。 */
    private final Duration invalidationGateTtl;

    /** 在业务事务内持久化缓存失效 Outbox 意图。 */
    private final ManagedCacheInvalidationOutboxMapper outboxMapper;

    /** 数据库提交后立即尝试发布 Outbox 事件。 */
    private final ManagedCacheInvalidationRelayService relayService;

    /**
     * 创建共享缓存失效协调器。
     *
     * @param invalidationGuard 缓存失效门禁
     * @param outboxMapper Outbox 数据访问组件
     * @param relayService 提交后事件中继
     * @param invalidationGateTtl pending 门禁恢复性 TTL
     */
    public ManagedCacheInvalidationCoordinator(
            CacheInvalidationGuard invalidationGuard,
            ManagedCacheInvalidationOutboxMapper outboxMapper,
            ManagedCacheInvalidationRelayService relayService,
            @Value("${payment.cache.redis.invalidation-gate-ttl:PT2H}")
            Duration invalidationGateTtl) {
        if (invalidationGateTtl == null
                || invalidationGateTtl.isZero()
                || invalidationGateTtl.isNegative()) {
            throw new IllegalArgumentException("Cache invalidation gate TTL must be positive");
        }
        this.invalidationGuard = invalidationGuard;
        this.outboxMapper = outboxMapper;
        this.relayService = relayService;
        this.invalidationGateTtl = invalidationGateTtl;
    }

    /**
     * 为当前事务和目标缓存 Key 准备一次可靠失效。
     *
     * @param cacheName 已登记的 Spring Cache 名称
     * @param businessKey 商户号或平台配置键
     */
    public void prepare(String cacheName, String businessKey) {
        requireTarget(cacheName, businessKey);
        requireTransactionSynchronization();
        String normalizedBusinessKey = businessKey.trim();
        Map<String, EventContext> events = transactionEvents();
        String target = cacheName + "\n" + normalizedBusinessKey;
        if (events.containsKey(target)) {
            return;
        }
        CacheInvalidationLease lease = invalidationGuard.acquire(
                cacheName,
                normalizedBusinessKey,
                invalidationGateTtl
        );
        ManagedCacheInvalidationOutboxDO event = newEvent(lease);
        events.put(target, new EventContext(event, lease));
        int inserted = outboxMapper.insertEvent(event);
        if (inserted != 1) {
            throw new IllegalStateException("Managed cache invalidation intent was not persisted");
        }
    }

    /**
     * 获取当前事务内按缓存目标去重的事件集合，并在首次访问时注册事务回调。
     *
     * @return 与当前事务生命周期绑定的有序事件集合
     */
    @SuppressWarnings("unchecked")
    private Map<String, EventContext> transactionEvents() {
        if (TransactionSynchronizationManager.hasResource(transactionResourceKey)) {
            return (Map<String, EventContext>) TransactionSynchronizationManager
                    .getResource(transactionResourceKey);
        }
        Map<String, EventContext> events = new LinkedHashMap<>();
        TransactionSynchronizationManager.bindResource(transactionResourceKey, events);
        TransactionSynchronizationManager.registerSynchronization(synchronization(events));
        return events;
    }

    /**
     * 创建事务同步器：提交后发布事件，回滚后释放门禁，并始终解绑事务资源。
     *
     * @param events 当前事务内已经持久化的失效事件
     * @return 绑定提交和完成回调的事务同步器
     */
    private TransactionSynchronization synchronization(Map<String, EventContext> events) {
        return new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                for (EventContext context : events.values()) {
                    try {
                        relayService.publish(context.event().getEventId());
                    } catch (RuntimeException exception) {
                        log.warn(
                                "event: MANAGED_CACHE_INVALIDATION_AFTER_COMMIT_FAILED "
                                        + "eventId: {} cacheName: {} exceptionType: {}",
                                context.event().getEventId(),
                                context.event().getCacheName(),
                                exception.getClass().getSimpleName()
                        );
                    }
                }
            }

            @Override
            public void afterCompletion(int status) {
                try {
                    if (status != STATUS_COMMITTED) {
                        events.values().forEach(context -> releaseAfterRollback(context.lease()));
                    }
                } finally {
                    if (TransactionSynchronizationManager.hasResource(transactionResourceKey)) {
                        TransactionSynchronizationManager.unbindResource(transactionResourceKey);
                    }
                }
            }
        };
    }

    /**
     * 数据库事务回滚后释放未生效门禁；释放异常不能覆盖原事务结果。
     *
     * @param lease 待释放租约
     */
    private void releaseAfterRollback(CacheInvalidationLease lease) {
        try {
            invalidationGuard.release(lease);
        } catch (RuntimeException exception) {
            log.warn(
                    "event: MANAGED_CACHE_INVALIDATION_ABORT_FAILED "
                            + "cacheName: {} businessKey: {} exceptionType: {}",
                    lease.cacheName(),
                    lease.businessKey(),
                    exception.getClass().getSimpleName()
            );
        }
    }

    /**
     * 将门禁租约转换为 INIT 状态 Outbox 事件。
     *
     * @param lease 已获取的门禁租约
     * @return 可在当前业务事务内持久化的事件
     */
    private ManagedCacheInvalidationOutboxDO newEvent(CacheInvalidationLease lease) {
        LocalDateTime now = LocalDateTime.now();
        ManagedCacheInvalidationOutboxDO event = new ManagedCacheInvalidationOutboxDO();
        event.setEventId("managed-cache-" + UUID.randomUUID());
        event.setCacheName(lease.cacheName());
        event.setBusinessKey(lease.businessKey());
        event.setGateToken(lease.token());
        event.setEventStatus("INIT");
        event.setRetryCount(0);
        event.setVersion(0);
        event.setCreateTime(now);
        event.setUpdateTime(now);
        return event;
    }

    /**
     * 校验精确失效目标，拒绝空 Cache Name 或业务键。
     *
     * @param cacheName Spring Cache 名称
     * @param businessKey 业务键
     */
    private void requireTarget(String cacheName, String businessKey) {
        if (!StringUtils.hasText(cacheName) || !StringUtils.hasText(businessKey)) {
            throw new IllegalArgumentException("Cache name and business key are required");
        }
    }

    /**
     * 要求调用方处于启用同步回调的真实数据库事务中。
     */
    private void requireTransactionSynchronization() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "Managed cache invalidation must be prepared inside an active database transaction"
            );
        }
    }

    /** 当前事务内单个缓存目标对应的 Outbox 事件和门禁租约。 */
    private record EventContext(ManagedCacheInvalidationOutboxDO event,
                                CacheInvalidationLease lease) {
    }
}
