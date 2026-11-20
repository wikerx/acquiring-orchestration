package com.scott.payment.risk.service.impl;

import com.scott.payment.risk.domain.MerchantLimitReservationTransitionSummary;
import com.scott.payment.risk.domain.PaymentPreparationReservationScope;
import com.scott.payment.risk.domain.state.MerchantLimitReservationStatus;
import com.scott.payment.risk.entity.MerchantLimitReservationDO;
import com.scott.payment.risk.service.MerchantLimitReservationCounterService;
import com.scott.payment.risk.service.MerchantLimitReservationStateService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 累计限额生命周期编排测试，验证 Redis 回滚必须先于持久状态取消且终态保持幂等。
 */
class DefaultMerchantLimitReservationLifecycleCoordinatorTests {

    /**
     * 验证按锁取消预占的支付状态入口保持事务边界。
     */
    @Test
    void shouldKeepPaymentStatusEntryTransactionalForLockedCancellation() throws NoSuchMethodException {
        Transactional transactional = DefaultMerchantLimitReservationLifecycleCoordinator.class
                .getMethod("applyPaymentStatus", String.class, String.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }

    /**
     * 验证确认预占时状态锁定和迁移加入同一生命周期事务。
     */
    @Test
    void shouldJoinLifecycleTransactionWhenConfirmingReservation() throws NoSuchMethodException {
        Transactional transactional = DefaultMerchantLimitReservationStateService.class
                .getMethod("confirm", String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }

    /**
     * 验证取消预占时先回滚 Redis，再持久化 CANCELLED 终态。
     */
    @Test
    void shouldRollbackRedisBeforeMarkingReservationCancelled() {
        MerchantLimitReservationStateService stateService =
                mock(MerchantLimitReservationStateService.class);
        MerchantLimitReservationCounterService counterService =
                mock(MerchantLimitReservationCounterService.class);
        MerchantLimitReservationDO reserved = reservation(
                MerchantLimitReservationStatus.RESERVED);
        when(stateService.lockByTransactionId("TX1001")).thenReturn(List.of(reserved));
        when(counterService.rollback(reserved)).thenReturn(true);
        when(stateService.cancelLocked(anyList(), eq("payment failed")))
                .thenReturn(new MerchantLimitReservationTransitionSummary(1, 0, 0));
        DefaultMerchantLimitReservationLifecycleCoordinator coordinator =
                new DefaultMerchantLimitReservationLifecycleCoordinator(stateService, counterService);

        MerchantLimitReservationTransitionSummary summary =
                coordinator.cancel("TX1001", "payment failed");

        assertThat(summary.applied()).isEqualTo(1);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(counterService, stateService);
        order.verify(counterService).rollback(reserved);
        order.verify(stateService).cancelLocked(List.of(reserved), "payment failed");
    }

    /**
     * 验证确认后的迟到失败事件不回滚 Redis 计数投影。
     */
    @Test
    void shouldNotRollbackRedisWhenLateFailureArrivesAfterConfirmation() {
        MerchantLimitReservationStateService stateService =
                mock(MerchantLimitReservationStateService.class);
        MerchantLimitReservationCounterService counterService =
                mock(MerchantLimitReservationCounterService.class);
        MerchantLimitReservationDO confirmed = reservation(
                MerchantLimitReservationStatus.CONFIRMED);
        when(stateService.lockByTransactionId("TX1001")).thenReturn(List.of(confirmed));
        when(stateService.cancelLocked(List.of(), "late failure"))
                .thenReturn(MerchantLimitReservationTransitionSummary.empty());
        DefaultMerchantLimitReservationLifecycleCoordinator coordinator =
                new DefaultMerchantLimitReservationLifecycleCoordinator(stateService, counterService);

        MerchantLimitReservationTransitionSummary summary =
                coordinator.applyPaymentStatus("TX1001", "FAILED", "late failure");

        assertThat(summary.conflicted()).isEqualTo(1);
        verify(counterService, never()).rollback(confirmed);
    }

    @Test
    void compensationCannotReleaseAConflictingMerchantGenerationOrLegacyReservation() {
        var state = mock(MerchantLimitReservationStateService.class);
        var counters = mock(MerchantLimitReservationCounterService.class);
        var coordinator = new DefaultMerchantLimitReservationLifecycleCoordinator(state, counters);
        LocalDateTime time = LocalDateTime.of(2026, 6, 30, 23, 59, 59);
        String key = "b".repeat(64);
        var protectedScope = new PaymentPreparationReservationScope("M1", key, 2L, time);
        var expected = withScope(reservation(MerchantLimitReservationStatus.RESERVED), protectedScope);
        var otherMerchant = withScope(reservation(MerchantLimitReservationStatus.RESERVED),
                new PaymentPreparationReservationScope("M2", key, 2L, time));
        var otherGeneration = withScope(reservation(MerchantLimitReservationStatus.RESERVED),
                new PaymentPreparationReservationScope("M1", key, 1L, time));
        var legacy = reservation(MerchantLimitReservationStatus.RESERVED);
        when(state.lockByTransactionId("TX1001")).thenReturn(List.of(otherMerchant, legacy, expected, otherGeneration));
        when(counters.rollback(expected)).thenReturn(true);
        when(counters.rollback(legacy)).thenReturn(true);
        when(state.cancelLocked(List.of(expected), "owned")).thenReturn(new MerchantLimitReservationTransitionSummary(1, 0, 0));
        when(state.cancelLocked(List.of(legacy), "legacy")).thenReturn(new MerchantLimitReservationTransitionSummary(1, 0, 0));

        assertThat(coordinator.cancelCompensation("TX1001", protectedScope, "owned").applied()).isEqualTo(1);
        assertThat(coordinator.cancelCompensation("TX1001", null, "legacy").applied()).isEqualTo(1);
        verify(counters).rollback(expected);
        verify(counters).rollback(legacy);
        verify(counters, never()).rollback(otherMerchant);
        verify(counters, never()).rollback(otherGeneration);
    }

    private MerchantLimitReservationDO withScope(MerchantLimitReservationDO reservation,
                                                  PaymentPreparationReservationScope scope) {
        reservation.setMerchantId(scope.merchantId());
        reservation.setPaymentPreparationKey(scope.key());
        reservation.setPaymentPreparationGeneration(scope.generation());
        reservation.setPaymentTransactionDateTime(scope.transactionTime());
        return reservation;
    }

    /**
     * 构造具有指定状态、版本和计数投影的预占事实。
     */
    private MerchantLimitReservationDO reservation(MerchantLimitReservationStatus status) {
        MerchantLimitReservationDO reservation = new MerchantLimitReservationDO();
        reservation.setId(1L);
        reservation.setTransactionId("TX1001");
        reservation.setReservationStatus(status.name());
        reservation.setVersion(1);
        return reservation;
    }
}
