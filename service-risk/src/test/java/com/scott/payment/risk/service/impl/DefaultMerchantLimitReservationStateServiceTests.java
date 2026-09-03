package com.scott.payment.risk.service.impl;

import com.scott.payment.risk.domain.MerchantLimitReservationTransitionSummary;
import com.scott.payment.risk.domain.state.MerchantLimitReservationStatus;
import com.scott.payment.risk.entity.MerchantLimitReservationDO;
import com.scott.payment.risk.mapper.MerchantLimitReservationMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultMerchantLimitReservationStateServiceTests
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 累计限额预占状态机测试，覆盖事务锁、乐观锁、幂等终态和非法反向迁移。
 * @status : create
 */
class DefaultMerchantLimitReservationStateServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T02:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    @Test
    void shouldSelectProductionConstructorWhenCreatedBySpring() {
        MerchantLimitReservationMapper mapper = mock(MerchantLimitReservationMapper.class);
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(MerchantLimitReservationMapper.class, () -> mapper);
            context.register(DefaultMerchantLimitReservationStateService.class);

            context.refresh();

            assertThat(context.getBean(DefaultMerchantLimitReservationStateService.class)).isNotNull();
        }
    }

    @Test
    void shouldRequireExistingTransactionForLockingRead() throws NoSuchMethodException {
        Transactional transactional = DefaultMerchantLimitReservationStateService.class
                .getMethod("lockByTransactionId", String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
    }

    @Test
    void shouldConfirmReservedReservationAndTreatDuplicateConfirmAsIdempotent() {
        MerchantLimitReservationMapper mapper = mock(MerchantLimitReservationMapper.class);
        MerchantLimitReservationDO reserved = reservation(7L, MerchantLimitReservationStatus.RESERVED, 3);
        when(mapper.selectByTransactionId("TX1001")).thenReturn(List.of(reserved));
        when(mapper.transitionStatus(
                7L,
                3,
                MerchantLimitReservationStatus.RESERVED.name(),
                MerchantLimitReservationStatus.CONFIRMED.name(),
                null,
                LocalDateTime.of(2026, 7, 30, 10, 0))).thenReturn(1);

        DefaultMerchantLimitReservationStateService service =
                new DefaultMerchantLimitReservationStateService(mapper, CLOCK);

        MerchantLimitReservationTransitionSummary first = service.confirm("TX1001");
        reserved.setReservationStatus(MerchantLimitReservationStatus.CONFIRMED.name());
        reserved.setVersion(4);
        MerchantLimitReservationTransitionSummary duplicate = service.confirm("TX1001");

        assertThat(first.applied()).isEqualTo(1);
        assertThat(first.idempotent()).isZero();
        assertThat(duplicate.applied()).isZero();
        assertThat(duplicate.idempotent()).isEqualTo(1);
    }

    @Test
    void shouldNotLetLateCancelOverwriteConfirmedReservation() {
        MerchantLimitReservationMapper mapper = mock(MerchantLimitReservationMapper.class);
        MerchantLimitReservationDO confirmed = reservation(9L, MerchantLimitReservationStatus.CONFIRMED, 4);
        when(mapper.selectByTransactionId("TX1002")).thenReturn(List.of(confirmed));
        DefaultMerchantLimitReservationStateService service =
                new DefaultMerchantLimitReservationStateService(mapper, CLOCK);

        MerchantLimitReservationTransitionSummary summary = service.cancel("TX1002", "late failure event");

        assertThat(summary.applied()).isZero();
        assertThat(summary.conflicted()).isEqualTo(1);
        verify(mapper, never()).transitionStatus(
                eq(9L), eq(4), any(), any(), any(), any());
    }

    @Test
    void shouldTreatConcurrentPreparingToReservedCasAsIdempotent() {
        MerchantLimitReservationMapper mapper = mock(MerchantLimitReservationMapper.class);
        MerchantLimitReservationDO preparing = reservation(
                11L, MerchantLimitReservationStatus.PREPARING, 0);
        MerchantLimitReservationDO latest = reservation(
                11L, MerchantLimitReservationStatus.RESERVED, 1);
        when(mapper.transitionStatus(
                11L,
                0,
                MerchantLimitReservationStatus.PREPARING.name(),
                MerchantLimitReservationStatus.RESERVED.name(),
                null,
                LocalDateTime.of(2026, 7, 30, 10, 0))).thenReturn(0);
        when(mapper.selectReservationById(11L)).thenReturn(latest);
        DefaultMerchantLimitReservationStateService service =
                new DefaultMerchantLimitReservationStateService(mapper, CLOCK);

        assertThat(service.markReserved(preparing)).isTrue();
    }

    private MerchantLimitReservationDO reservation(Long id,
                                                   MerchantLimitReservationStatus status,
                                                   int version) {
        MerchantLimitReservationDO reservation = new MerchantLimitReservationDO();
        reservation.setId(id);
        reservation.setTransactionId(id == 7L ? "TX1001" : "TX1002");
        reservation.setReservationStatus(status.name());
        reservation.setVersion(version);
        return reservation;
    }
}
