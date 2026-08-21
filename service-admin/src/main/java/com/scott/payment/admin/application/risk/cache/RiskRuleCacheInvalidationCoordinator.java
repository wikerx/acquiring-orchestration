package com.scott.payment.admin.application.risk.cache;

import com.scott.payment.admin.entity.RiskCacheInvalidationOutboxDO;
import com.scott.payment.admin.mapper.RiskCacheInvalidationOutboxMapper;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.generation.RedisCachePublication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 将风控规则缓存失效意图绑定到当前数据库事务。
 */
@Slf4j
@Service
public class RiskRuleCacheInvalidationCoordinator {

    /** 现有风控规则 generation 机制使用的兼容命名空间。 */
    static final String CACHE_NAMESPACE = "risk-runtime-rule";

    /** 业务事务提交前 generation 发布门禁的最长有效期。 */
    static final Duration PUBLICATION_GATE_TTL = Duration.ofMinutes(30);

    /** 当前数据库事务绑定单个失效事件时使用的实例级资源键。 */
    private final Object transactionResourceKey = new Object();

    /** 申请、提交和回滚风控规则缓存 generation。 */
    private final RedisCacheGenerationStore generationStore;

    /** 在业务事务内持久化 generation 发布 Outbox 意图。 */
    private final RiskCacheInvalidationOutboxMapper outboxMapper;

    /** 事务提交后立即尝试切换 generation。 */
    private final RiskCacheInvalidationRelayService relayService;

    /**
     * 创建与数据库事务同步的风控规则缓存失效协调器。
     *
     * @param generationStore Redis generation 状态存储
     * @param outboxMapper 风控缓存失效 Outbox 数据访问组件
     * @param relayService 提交后 generation 发布中继服务
     */
    public RiskRuleCacheInvalidationCoordinator(RedisCacheGenerationStore generationStore,
                                                RiskCacheInvalidationOutboxMapper outboxMapper,
                                                RiskCacheInvalidationRelayService relayService) {
        this.generationStore = generationStore;
        this.outboxMapper = outboxMapper;
        this.relayService = relayService;
    }

    /**
     * 为当前事务准备一次持久化缓存失效发布。
     */
    public void prepare() {
        requireTransactionSynchronization();
        if (TransactionSynchronizationManager.hasResource(transactionResourceKey)) {
            return;
        }
        RedisCachePublication publication =
                generationStore.begin(CACHE_NAMESPACE, PUBLICATION_GATE_TTL);
        RiskCacheInvalidationOutboxDO event = newEvent(publication);
        TransactionSynchronizationManager.bindResource(transactionResourceKey, event);
        TransactionSynchronizationManager.registerSynchronization(
                synchronization(event, publication)
        );
        int inserted = outboxMapper.insertEvent(event);
        if (inserted != 1) {
            throw new IllegalStateException("Risk cache invalidation intent was not persisted");
        }
    }

    /**
     * 创建事务回调：提交后发布失效事件，回滚后释放 generation 发布门禁。
     *
     * @param event       与业务写入同事务保存的 Outbox 事件
     * @param publication 当前事务持有的 generation 发布凭证
     * @return 绑定到当前数据库事务的同步回调
     */
    private TransactionSynchronization synchronization(RiskCacheInvalidationOutboxDO event,
                                                       RedisCachePublication publication) {
        return new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    relayService.publish(event.getEventId());
                } catch (RuntimeException exception) {
                    log.warn(
                            "event: RISK_CACHE_INVALIDATION_AFTER_COMMIT_FAILED eventId: {} exceptionType: {}",
                            event.getEventId(),
                            exception.getClass().getSimpleName()
                    );
                }
            }

            @Override
            public void afterCompletion(int status) {
                try {
                    if (status != STATUS_COMMITTED) {
                        generationStore.abort(publication);
                    }
                } catch (RuntimeException exception) {
                    log.warn(
                            "event: RISK_CACHE_INVALIDATION_ABORT_FAILED eventId: {} exceptionType: {}",
                            event.getEventId(),
                            exception.getClass().getSimpleName()
                    );
                } finally {
                    if (TransactionSynchronizationManager.hasResource(transactionResourceKey)) {
                        TransactionSynchronizationManager.unbindResource(transactionResourceKey);
                    }
                }
            }
        };
    }

    /**
     * 将预留的 generation 发布凭证转换为 INIT 状态 Outbox 事件。
     *
     * @param publication 当前事务持有的 generation 发布凭证
     * @return 可与业务数据同事务持久化的失效事件
     */
    private RiskCacheInvalidationOutboxDO newEvent(RedisCachePublication publication) {
        LocalDateTime now = LocalDateTime.now();
        RiskCacheInvalidationOutboxDO event = new RiskCacheInvalidationOutboxDO();
        event.setEventId("risk-cache-" + UUID.randomUUID());
        event.setNamespace(publication.namespace());
        event.setPublicationToken(publication.token());
        event.setGeneration(publication.generation());
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
                    "Risk cache invalidation must be prepared inside an active database transaction"
            );
        }
    }
}
