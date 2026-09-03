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
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinCacheInvalidationCoordinator
 * @date : 2026-08-24 00:00
 * @email : scott_x@163.com
 * @description : Card BIN 缓存失效协调器，在管理端数据库事务内冻结 generation 变更 Outbox，提交后切换 Redis，回滚时释放发布门禁。
 * @status : create
 */
@Slf4j
@Service
public class CardBinCacheInvalidationCoordinator {

    /**
     * {@code CACHE_NAMESPACE}，用于展示或识别当前商户、渠道、用户、角色、模板或配置对象。
     * <p>
     * 单位：无；格式：固定协议字面量或受控编码；不允许为空；非敏感字段。
     * 取值范围：取值由当前类对接的协议、状态机或配置约定限定；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
     */
    public static final String CACHE_NAMESPACE = "card-bin-range";
    /**
     * {@code PUBLICATION_GATE_TTL}常量，统一 {@code CardBinCacheInvalidationCoordinator} 内部使用的配置值、状态码或协议字段。
     * <p>
     * 单位：无；格式：字符串、对象引用或集合结构；不允许为空；非敏感字段。
     * 取值范围：取值范围受数据库字段长度、Bean Validation、接口协议或配置枚举约束；数据来源：当前业务流程上游模型、配置项或数据库查询结果。
     * </p>
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

    private TransactionSynchronization synchronization(RedisCachePublication publication) {
        return new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    if (!generationStore.commit(publication)) {
                        log.warn("event: CARD_BIN_CACHE_GENERATION_COMMIT_DEFERRED generation: {}",
                                publication.generation());
                    }
                } catch (RuntimeException exception) {
                    log.warn("event: CARD_BIN_CACHE_GENERATION_COMMIT_FAILED exceptionType: {}",
                            exception.getClass().getSimpleName());
                }
            }

            @Override
            public void afterCompletion(int status) {
                try {
                    if (status != STATUS_COMMITTED) {
                        generationStore.abort(publication);
                    }
                } catch (RuntimeException exception) {
                    log.warn("event: CARD_BIN_CACHE_GENERATION_ABORT_FAILED exceptionType: {}",
                            exception.getClass().getSimpleName());
                } finally {
                    if (TransactionSynchronizationManager.hasResource(transactionResourceKey)) {
                        TransactionSynchronizationManager.unbindResource(transactionResourceKey);
                    }
                }
            }
        };
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Card BIN cache invalidation requires an active database transaction");
        }
    }
}
