package com.scott.payment.admin.application.cache;

import com.scott.payment.admin.entity.MerchantSecurityCacheInvalidationOutboxDO;
import com.scott.payment.admin.mapper.MerchantSecurityCacheInvalidationOutboxMapper;
import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.CacheInvalidationLease;
import com.scott.payment.component.redis.cache.PaymentCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantSecurityCacheInvalidationCoordinator
 * @date : 2026-07-30 00:00
 * @email : scott_x@163.com
 * @description : 管理端应用层永久缓存失效协调器，将 pending 门禁与 Outbox 失效意图绑定到当前数据库事务
 * @status : create
 *
 * <p>类名与底层 Outbox 表名保留“MerchantSecurity”是为了兼容已部署表和历史待处理事件；
 * 事件模型本身以 cacheName 和 businessKey 寻址，现同时承载商户资料、OpenAPI 策略和白名单内
 * 平台公开配置的可靠失效。</p>
 */
@Slf4j
@Service
public class MerchantSecurityCacheInvalidationCoordinator {

    /** 当前数据库事务绑定事件集合时使用的实例级资源键。 */
    private final Object transactionResourceKey = new Object();

    /** 在业务提交完成前阻止旧缓存值重新写回的失效门闩。 */
    private final CacheInvalidationGuard invalidationGuard;

    /** 提供失效门闩 TTL 等统一缓存配置。 */
    private final PaymentCacheProperties cacheProperties;

    /** 在业务事务内持久化缓存失效 Outbox 意图。 */
    private final MerchantSecurityCacheInvalidationOutboxMapper outboxMapper;

    /** 事务提交后立即尝试发布 Outbox 事件。 */
    private final MerchantSecurityCacheInvalidationRelayService relayService;

    /**
     * 创建与数据库事务同步的商户安全缓存失效协调器。
     *
     * @param invalidationGuard 缓存失效门闩服务
     * @param cacheProperties 缓存失效统一配置
     * @param outboxMapper Outbox 数据访问组件
     * @param relayService 提交后事件中继服务
     */
    public MerchantSecurityCacheInvalidationCoordinator(
            CacheInvalidationGuard invalidationGuard,
            PaymentCacheProperties cacheProperties,
            MerchantSecurityCacheInvalidationOutboxMapper outboxMapper,
            MerchantSecurityCacheInvalidationRelayService relayService) {
        this.invalidationGuard = invalidationGuard;
        this.cacheProperties = cacheProperties;
        this.outboxMapper = outboxMapper;
        this.relayService = relayService;
    }

    /**
     * 为当前事务和目标缓存 Key 准备一次可靠失效。
     *
     * @param cacheName   Spring Cache 名称
     * @param businessKey 商户号或平台配置键
     */
    public void prepare(String cacheName, String businessKey) {
        requireTransactionSynchronization();
        Map<String, EventContext> events = transactionEvents();
        String target = cacheName + "\n" + businessKey;
        if (events.containsKey(target)) {
            return;
        }
        CacheInvalidationLease lease = invalidationGuard.acquire(
                cacheName,
                businessKey,
                cacheProperties.getInvalidationGateTtl()
        );
        MerchantSecurityCacheInvalidationOutboxDO event = newEvent(lease);
        events.put(target, new EventContext(event, lease));
        int inserted = outboxMapper.insertEvent(event);
        if (inserted != 1) {
            throw new IllegalStateException("Managed persistent cache invalidation intent was not persisted");
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
     * 创建事务同步器：提交后发布事件，回滚后释放门闩，并始终解绑事务资源。
     *
     * @param events 当前事务内已持久化的失效事件
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
                                "event: MERCHANT_SECURITY_CACHE_INVALIDATION_AFTER_COMMIT_FAILED "
                                        + "eventId: {} cacheName: {} reason: {}",
                                context.event().getEventId(),
                                context.event().getCacheName(),
                                exception.getMessage()
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
     * 业务事务回滚后释放未生效的失效门闩；释放异常仅记录，不覆盖原事务结果。
     *
     * @param lease 待释放的缓存失效租约
     */
    private void releaseAfterRollback(CacheInvalidationLease lease) {
        try {
            invalidationGuard.release(lease);
        } catch (RuntimeException exception) {
            log.warn(
                    "event: MERCHANT_SECURITY_CACHE_INVALIDATION_ABORT_FAILED "
                            + "cacheName: {} businessKey: {} reason: {}",
                    lease.cacheName(),
                    lease.businessKey(),
                    exception.getMessage()
            );
        }
    }

    /**
     * 将门闩租约转换为 INIT 状态的 Outbox 事件。
     *
     * @param lease 已获取的缓存失效租约
     * @return 可在当前业务事务内持久化的事件
     */
    private MerchantSecurityCacheInvalidationOutboxDO newEvent(CacheInvalidationLease lease) {
        LocalDateTime now = LocalDateTime.now();
        MerchantSecurityCacheInvalidationOutboxDO event =
                new MerchantSecurityCacheInvalidationOutboxDO();
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
     * 要求调用方处于已启用同步回调的真实数据库事务中。
     *
     * @throws IllegalStateException 无活动事务或事务同步未启用时抛出
     */
    private void requireTransactionSynchronization() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException(
                    "Managed persistent cache invalidation must be prepared inside an active database transaction"
            );
        }
    }

    private record EventContext(MerchantSecurityCacheInvalidationOutboxDO event,
                                CacheInvalidationLease lease) {
    }
}
