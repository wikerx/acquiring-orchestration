package com.scott.payment.risk.service.impl;

import com.scott.payment.risk.domain.MerchantLimitReservationTransitionSummary;
import com.scott.payment.risk.domain.state.MerchantLimitReservationStatus;
import com.scott.payment.risk.entity.MerchantLimitReservationDO;
import com.scott.payment.risk.service.MerchantLimitReservationCounterService;
import com.scott.payment.risk.service.MerchantLimitReservationStateService;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultMerchantLimitReservationLifecycleCoordinatorTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 累计限额生命周期编排测试，验证 Redis 回滚必须先于持久状态取消且终态保持幂等。
 * @status : create
 */
class DefaultMerchantLimitReservationLifecycleCoordinatorTests {

    @Test
    void shouldKeepPaymentStatusEntryTransactionalForLockedCancellation() throws NoSuchMethodException {
        Transactional transactional = DefaultMerchantLimitReservationLifecycleCoordinator.class
                .getMethod("applyPaymentStatus", String.class, String.class, String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
    }

    @Test
    void shouldJoinLifecycleTransactionWhenConfirmingReservation() throws NoSuchMethodException {
        Transactional transactional = DefaultMerchantLimitReservationStateService.class
                .getMethod("confirm", String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }

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

    private MerchantLimitReservationDO reservation(MerchantLimitReservationStatus status) {
        MerchantLimitReservationDO reservation = new MerchantLimitReservationDO();
        reservation.setId(1L);
        reservation.setTransactionId("TX1001");
        reservation.setReservationStatus(status.name());
        reservation.setVersion(1);
        return reservation;
    }
}
