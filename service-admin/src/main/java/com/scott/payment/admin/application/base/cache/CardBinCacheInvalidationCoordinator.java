package com.scott.payment.admin.application.base.cache;

import com.scott.payment.component.core.trace.TraceContext;
import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.CacheGenerationChangedMessage;
import com.scott.payment.component.mq.publisher.ReliableMqPublisher;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.generation.RedisCachePublication;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Card BIN 缓存发布协调器，在管理端数据库事务内冻结 generation 和可靠 MQ Outbox，激活工作交给从库确认后的支付消费者
 */
@Slf4j
@Service
public class CardBinCacheInvalidationCoordinator {

    /**
     * Card BIN 范围索引在 Redis 中使用的缓存命名空间。
     */
    public static final String CACHE_NAMESPACE = "card-bin-range";

    /**
     * 缓存代际发布门闩的有效期，防止发布事务异常后长期阻塞后续刷新。
     */
    static final Duration PUBLICATION_GATE_TTL = Duration.ofMinutes(30);

    private final Object transactionResourceKey = new Object();
    private final RedisCacheGenerationStore generationStore;
    private final ReliableMqPublisher reliableMqPublisher;

    /**
     * @param generationStore Redis generation 原子存储
     * @param reliableMqPublisher 与管理端主库事务绑定的可靠 MQ 发布器
     */
    public CardBinCacheInvalidationCoordinator(RedisCacheGenerationStore generationStore,
                                               ReliableMqPublisher reliableMqPublisher) {
        this.generationStore = generationStore;
        this.reliableMqPublisher = reliableMqPublisher;
    }

    /**
     * 为当前 Card BIN 写事务准备一次失效；同一事务重复调用只产生一个 generation 和 Outbox。
     */
    public void prepare() {
        requireTransaction();
        if (TransactionSynchronizationManager.hasResource(transactionResourceKey)) {
            return;
        }
        RedisCachePublication publication = generationStore.begin(CACHE_NAMESPACE, PUBLICATION_GATE_TTL);
        TransactionSynchronizationManager.bindResource(transactionResourceKey, publication);
        TransactionSynchronizationManager.registerSynchronization(synchronization(publication));
        reliableMqPublisher.publish(
                MqTopic.CACHE_INVALIDATION,
                MqTag.CARD_BIN_CACHE_CHANGED,
                message(publication));
    }

    /**
     * 解析业务结果的本地化说明。
     */
    private CacheGenerationChangedMessage message(RedisCachePublication publication) {
        CacheGenerationChangedMessage message = new CacheGenerationChangedMessage();
        message.setMessageId("card-bin-cache-" + publication.generation());
        message.setCreatedAt(LocalDateTime.now());
        message.setTraceId(TraceContext.getOrCreateTraceId());
        message.setRetryCount(0);
        message.setNamespace(publication.namespace());
        message.setPublicationToken(publication.token());
        message.setGeneration(publication.generation());
        message.setEventType(MqTag.CARD_BIN_CACHE_CHANGED);
        return message;
    }

    /**
     * 创建事务提交后的缓存失效回调。
     */
    private TransactionSynchronization synchronization(RedisCachePublication publication) {
        return new TransactionSynchronization() {
            /**
             * 在事务回滚时撤销未提交的卡 BIN 缓存代际发布凭证。
             */
            @Override
            public void afterCompletion(int status) {
                try {
                    if (status != STATUS_COMMITTED) {
                        generationStore.abort(publication);
                    }
                } catch (RuntimeException exception) {
                    log.warn("卡BIN缓存代际终止失败, event: CARD_BIN_CACHE_GENERATION_ABORT_FAILED, exceptionType: {}",
                            exception.getClass().getSimpleName());
                } finally {
                    if (TransactionSynchronizationManager.hasResource(transactionResourceKey)) {
                        TransactionSynchronizationManager.unbindResource(transactionResourceKey);
                    }
                }
            }
        };
    }

    /**
     * 校验并获取交易。
     */
    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Card BIN cache invalidation requires an active database transaction");
        }
    }
}
