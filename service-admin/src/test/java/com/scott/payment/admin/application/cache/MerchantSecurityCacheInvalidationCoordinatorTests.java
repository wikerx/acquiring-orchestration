package com.scott.payment.admin.application.cache;

import com.scott.payment.admin.entity.MerchantSecurityCacheInvalidationOutboxDO;
import com.scott.payment.admin.mapper.MerchantSecurityCacheInvalidationOutboxMapper;
import com.scott.payment.component.core.cache.CacheInvalidationGuard;
import com.scott.payment.component.core.cache.CacheInvalidationLease;
import com.scott.payment.component.core.cache.PaymentCacheNames;
import com.scott.payment.component.redis.cache.PaymentCacheProperties;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 受管永久缓存失效事务协调测试。
 */
@Slf4j
class MerchantSecurityCacheInvalidationCoordinatorTests {

    /**
     * 清理测试线程上的事务同步状态，避免用例之间共享资源。
     */
    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    /**
     * 验证同一事务内相同缓存目标只持久化并发布一次。
     */
    @Test
    void shouldPersistAndPublishOneTargetOnlyOncePerTransaction() {
        log.info("测试永久缓存事务内去重，关键输入: merchant:info/200045 重复准备两次");
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        MerchantSecurityCacheInvalidationOutboxMapper mapper =
                mock(MerchantSecurityCacheInvalidationOutboxMapper.class);
        MerchantSecurityCacheInvalidationRelayService relay =
                mock(MerchantSecurityCacheInvalidationRelayService.class);
        CacheInvalidationLease lease = new CacheInvalidationLease(
                PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
                "200045",
                "t-owner"
        );
        when(guard.acquire(
                PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
                "200045",
                Duration.ofHours(2)
        )).thenReturn(lease);
        when(mapper.insertEvent(any())).thenReturn(1);
        MerchantSecurityCacheInvalidationCoordinator coordinator =
                coordinator(guard, mapper, relay);
        beginTransactionSynchronization();

        coordinator.prepare(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE, "200045");
        coordinator.prepare(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE, "200045");

        ArgumentCaptor<MerchantSecurityCacheInvalidationOutboxDO> eventCaptor =
                ArgumentCaptor.forClass(MerchantSecurityCacheInvalidationOutboxDO.class);
        verify(mapper).insertEvent(eventCaptor.capture());
        verify(guard).acquire(
                PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
                "200045",
                Duration.ofHours(2)
        );
        MerchantSecurityCacheInvalidationOutboxDO event = eventCaptor.getValue();
        assertThat(event.getEventId()).startsWith("managed-cache-");
        assertThat(event.getCacheName()).isEqualTo(PaymentCacheNames.MERCHANT_RUNTIME_PROFILE);
        assertThat(event.getBusinessKey()).isEqualTo("200045");
        assertThat(event.getGateToken()).isEqualTo("t-owner");
        assertThat(event.getEventStatus()).isEqualTo("INIT");

        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCommit();
        synchronization.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);

        verify(relay).publish(event.getEventId());
        verify(guard, never()).release(lease);
        log.info("永久缓存事务内去重测试完成，结果: 仅持久化并发布一个 Outbox 事件");
    }

    /**
     * 验证业务事务回滚时不发布 Outbox，并仅由原 token 持有者释放门禁。
     */
    @Test
    void shouldReleaseOwnedGateWithoutPublishingAfterRollback() {
        log.info("测试永久缓存事务回滚，关键输入: merchant:openapi/200045");
        CacheInvalidationGuard guard = mock(CacheInvalidationGuard.class);
        MerchantSecurityCacheInvalidationOutboxMapper mapper =
                mock(MerchantSecurityCacheInvalidationOutboxMapper.class);
        MerchantSecurityCacheInvalidationRelayService relay =
                mock(MerchantSecurityCacheInvalidationRelayService.class);
        CacheInvalidationLease lease = new CacheInvalidationLease(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                "200045",
                "t-owner"
        );
        when(guard.acquire(
                PaymentCacheNames.MERCHANT_OPENAPI_ACCESS,
                "200045",
                Duration.ofHours(2)
        )).thenReturn(lease);
        when(mapper.insertEvent(any())).thenReturn(1);
        MerchantSecurityCacheInvalidationCoordinator coordinator =
                coordinator(guard, mapper, relay);
        beginTransactionSynchronization();

        coordinator.prepare(PaymentCacheNames.MERCHANT_OPENAPI_ACCESS, "200045");
        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);
        synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);

        verify(guard).release(lease);
        verify(relay, never()).publish(any());
        log.info("永久缓存事务回滚测试完成，结果: 门禁已释放且 Outbox 未发布");
    }

    /**
     * 验证协调器不能在数据库事务之外创建不可提交的失效意图。
     */
    @Test
    void shouldRejectPreparationOutsideDatabaseTransaction() {
        log.info("测试永久缓存事务门禁，关键输入: 无活动数据库事务");
        MerchantSecurityCacheInvalidationCoordinator coordinator = coordinator(
                mock(CacheInvalidationGuard.class),
                mock(MerchantSecurityCacheInvalidationOutboxMapper.class),
                mock(MerchantSecurityCacheInvalidationRelayService.class)
        );

        assertThatIllegalStateException().isThrownBy(() -> coordinator.prepare(
            PaymentCacheNames.MERCHANT_RUNTIME_PROFILE,
            "200045"
        )).withMessageContaining("active database transaction");
        log.info("永久缓存事务门禁测试完成，结果: 非事务调用被拒绝");
    }

    /**
     * 创建协调器测试实例。
     *
     * @param guard 失效门禁
     * @param mapper Outbox Mapper
     * @param relay Outbox 中继服务
     * @return 协调器测试实例
     */
    private MerchantSecurityCacheInvalidationCoordinator coordinator(
            CacheInvalidationGuard guard,
            MerchantSecurityCacheInvalidationOutboxMapper mapper,
            MerchantSecurityCacheInvalidationRelayService relay) {
        return new MerchantSecurityCacheInvalidationCoordinator(
                guard,
                new PaymentCacheProperties(),
                mapper,
                relay
        );
    }

    /**
     * 在当前测试线程启动可注册回调的模拟数据库事务。
     */
    private void beginTransactionSynchronization() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
    }
}
