package com.scott.payment.admin.application.risk.cache;

import com.scott.payment.admin.entity.RiskCacheInvalidationOutboxDO;
import com.scott.payment.admin.mapper.RiskCacheInvalidationOutboxMapper;
import com.scott.payment.component.redis.generation.RedisCacheGenerationStore;
import com.scott.payment.component.redis.generation.RedisCachePublication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : RiskRuleCacheInvalidationCoordinatorTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 风控规则缓存失效事务协调测试。
 * @status : create
 */
class RiskRuleCacheInvalidationCoordinatorTests {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void shouldPersistAndPublishOnlyOnceForOneTransaction() {
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        RiskCacheInvalidationOutboxMapper outboxMapper = mock(RiskCacheInvalidationOutboxMapper.class);
        RiskCacheInvalidationRelayService relayService = mock(RiskCacheInvalidationRelayService.class);
        RedisCachePublication publication = new RedisCachePublication(
                "risk-runtime-rule",
                "t-owner",
                "g-next"
        );
        when(generationStore.begin("risk-runtime-rule", Duration.ofMinutes(30)))
                .thenReturn(publication);
        when(outboxMapper.insertEvent(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        RiskRuleCacheInvalidationCoordinator coordinator = new RiskRuleCacheInvalidationCoordinator(
                generationStore,
                outboxMapper,
                relayService
        );
        beginTransactionSynchronization();

        coordinator.prepare();
        coordinator.prepare();

        ArgumentCaptor<RiskCacheInvalidationOutboxDO> eventCaptor =
                ArgumentCaptor.forClass(RiskCacheInvalidationOutboxDO.class);
        verify(outboxMapper).insertEvent(eventCaptor.capture());
        verify(generationStore).begin("risk-runtime-rule", Duration.ofMinutes(30));
        RiskCacheInvalidationOutboxDO event = eventCaptor.getValue();
        assertThat(event.getEventId()).isNotBlank();
        assertThat(event.getNamespace()).isEqualTo("risk-runtime-rule");
        assertThat(event.getPublicationToken()).isEqualTo("t-owner");
        assertThat(event.getGeneration()).isEqualTo("g-next");
        assertThat(event.getEventStatus()).isEqualTo("INIT");

        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCommit();
        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(relayService).publish(event.getEventId());
        verify(generationStore, never()).abort(publication);
    }

    @Test
    void shouldAbortPublicationWithoutPublishingAfterRollback() {
        RedisCacheGenerationStore generationStore = mock(RedisCacheGenerationStore.class);
        RiskCacheInvalidationOutboxMapper outboxMapper = mock(RiskCacheInvalidationOutboxMapper.class);
        RiskCacheInvalidationRelayService relayService = mock(RiskCacheInvalidationRelayService.class);
        RedisCachePublication publication = new RedisCachePublication(
                "risk-runtime-rule",
                "t-owner",
                "g-next"
        );
        when(generationStore.begin("risk-runtime-rule", Duration.ofMinutes(30)))
                .thenReturn(publication);
        when(outboxMapper.insertEvent(org.mockito.ArgumentMatchers.any())).thenReturn(1);
        RiskRuleCacheInvalidationCoordinator coordinator = new RiskRuleCacheInvalidationCoordinator(
                generationStore,
                outboxMapper,
                relayService
        );
        beginTransactionSynchronization();

        coordinator.prepare();

        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(generationStore).abort(publication);
        verify(relayService, never()).publish(org.mockito.ArgumentMatchers.anyString());
    }

    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
}
