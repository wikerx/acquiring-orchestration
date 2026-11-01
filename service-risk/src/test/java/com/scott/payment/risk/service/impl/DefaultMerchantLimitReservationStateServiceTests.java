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
 * 累计限额预占状态机测试，覆盖事务锁、乐观锁、幂等终态和非法反向迁移。
 */
class DefaultMerchantLimitReservationStateServiceTests {

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-30T02:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    /**
     * 验证 Spring 在多构造器场景下明确选择生产装配构造器。
     */
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

    /**
     * 验证锁定读取必须加入已有事务，禁止脱离状态迁移边界。
     */
    @Test
    void shouldRequireExistingTransactionForLockingRead() throws NoSuchMethodException {
        Transactional transactional = DefaultMerchantLimitReservationStateService.class
                .getMethod("lockByTransactionId", String.class)
                .getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.MANDATORY);
    }

    /**
     * 验证 RESERVED 可确认，重复确认返回幂等结果。
     */
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

    /**
     * 验证迟到取消不能覆盖已确认预占终态。
     */
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

    /**
     * 验证并发 CAS 已完成 PREPARING 到 RESERVED 时按幂等成功处理。
     */
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

    /**
     * 构造具有指定状态、版本和计数投影的预占事实。
     */
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
