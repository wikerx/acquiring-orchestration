package com.scott.payment.admin.application.base.cache;

import com.scott.payment.component.mq.constant.MqTag;
import com.scott.payment.component.mq.constant.MqTopic;
import com.scott.payment.component.mq.message.CacheGenerationChangedMessage;
import com.scott.payment.component.mq.publisher.ReliableMqPublisher;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.generation.RedisCachePublication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : CardBinCacheInvalidationCoordinatorTests
 * @date : 2026-08-24 00:00
 * @email : scott_x@163.com
 * @description : 验证 Card BIN 数据库事务与 Redis generation、可靠 MQ Outbox 的提交和回滚契约。
 * @status : create
 */
class CardBinCacheInvalidationCoordinatorTests {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void shouldPublishOneStableOutboxMessageAndCommitGenerationAfterTransactionCommit() {
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        ReliableMqPublisher publisher = mock(ReliableMqPublisher.class);
        RedisCachePublication publication = publication();
        when(generationStore.begin(CardBinCacheInvalidationCoordinator.CACHE_NAMESPACE,
                Duration.ofMinutes(30))).thenReturn(publication);
        when(generationStore.commit(publication)).thenReturn(true);
        CardBinCacheInvalidationCoordinator coordinator =
                new CardBinCacheInvalidationCoordinator(generationStore, publisher);
        beginTransactionSynchronization();

        coordinator.prepare();
        coordinator.prepare();

        ArgumentCaptor<CacheGenerationChangedMessage> messageCaptor =
                ArgumentCaptor.forClass(CacheGenerationChangedMessage.class);
        verify(publisher).publish(
                org.mockito.ArgumentMatchers.eq(MqTopic.CACHE_INVALIDATION),
                org.mockito.ArgumentMatchers.eq(MqTag.CARD_BIN_CACHE_CHANGED),
                messageCaptor.capture());
        CacheGenerationChangedMessage message = messageCaptor.getValue();
        assertThat(message.getMessageId()).isEqualTo("card-bin-cache-g-next");
        assertThat(message.getNamespace()).isEqualTo(CardBinCacheInvalidationCoordinator.CACHE_NAMESPACE);
        assertThat(message.getPublicationToken()).isEqualTo("t-owner");
        assertThat(message.getGeneration()).isEqualTo("g-next");
        assertThat(message.getEventType()).isEqualTo(MqTag.CARD_BIN_CACHE_CHANGED);

        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCommit();
        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(generationStore).commit(publication);
        verify(generationStore, never()).abort(publication);
        verify(generationStore, times(1)).begin(
                CardBinCacheInvalidationCoordinator.CACHE_NAMESPACE, Duration.ofMinutes(30));
    }

    @Test
    void shouldAbortPublicationAndNotCommitGenerationAfterRollback() {
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        ReliableMqPublisher publisher = mock(ReliableMqPublisher.class);
        RedisCachePublication publication = publication();
        when(generationStore.begin(CardBinCacheInvalidationCoordinator.CACHE_NAMESPACE,
                Duration.ofMinutes(30))).thenReturn(publication);
        CardBinCacheInvalidationCoordinator coordinator =
                new CardBinCacheInvalidationCoordinator(generationStore, publisher);
        beginTransactionSynchronization();

        coordinator.prepare();
        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(generationStore).abort(publication);
        verify(generationStore, never()).commit(any());
    }

    private RedisCachePublication publication() {
        return new RedisCachePublication(
                CardBinCacheInvalidationCoordinator.CACHE_NAMESPACE, "t-owner", "g-next");
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
}
