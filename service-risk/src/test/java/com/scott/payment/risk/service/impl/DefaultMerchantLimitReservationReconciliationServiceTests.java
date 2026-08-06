package com.scott.payment.risk.service.impl;

import com.scott.payment.risk.config.RiskEvaluationProperties;
import com.scott.payment.risk.domain.MerchantLimitReservationTransitionSummary;
import com.scott.payment.risk.domain.PaymentTransactionLookupResult;
import com.scott.payment.risk.domain.RedisReservationMarkerState;
import com.scott.payment.risk.domain.state.MerchantLimitReservationStatus;
import com.scott.payment.risk.entity.MerchantLimitReservationDO;
import com.scott.payment.risk.repository.RiskPaymentTransactionStatusRepository;
import com.scott.payment.risk.service.MerchantLimitReservationCounterService;
import com.scott.payment.risk.service.MerchantLimitReservationLifecycleCoordinator;
import com.scott.payment.risk.service.MerchantLimitReservationStateService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : DefaultMerchantLimitReservationReconciliationServiceTests
 * @date : 2026-07-30 22:55
 * @email : scott_x@163.com
 * @description : 验证累计限额预占对账只依据明确 Redis marker 和支付事实推进，未知状态一律保守保留
 * @status : create
 */
@Slf4j
class DefaultMerchantLimitReservationReconciliationServiceTests {

    /**
     * 验证可恢复 PREPARING、超过宽限期的无支付记录和处理中支付分别进入正确分支。
     */
    @Test
    void shouldRecoverPreparingAndCancelAbsentPaymentAfterGraceButRetainPending() {
        log.info("测试预占对账主路径，关键输入: marker 存在、支付缺失和支付处理中各一条");
        MerchantLimitReservationStateService stateService =
                mock(MerchantLimitReservationStateService.class);
        MerchantLimitReservationCounterService counterService =
                mock(MerchantLimitReservationCounterService.class);
        MerchantLimitReservationLifecycleCoordinator coordinator =
                mock(MerchantLimitReservationLifecycleCoordinator.class);
        RiskPaymentTransactionStatusRepository statusRepository =
                mock(RiskPaymentTransactionStatusRepository.class);
        LocalDateTime now = LocalDateTime.of(2026, 7, 30, 10, 0);
        MerchantLimitReservationDO preparing = reservation(
                1L, "TX-PREPARING", MerchantLimitReservationStatus.PREPARING, now.minusMinutes(10));
        MerchantLimitReservationDO absent = reservation(
                2L, "TX-ABSENT", MerchantLimitReservationStatus.RESERVED, now.minusMinutes(10));
        MerchantLimitReservationDO pending = reservation(
                3L, "TX-PENDING", MerchantLimitReservationStatus.RESERVED, now.minusMinutes(10));
        when(stateService.findStaleNonTerminal(now.minusSeconds(60), 100))
                .thenReturn(List.of(preparing, absent, pending));
        when(counterService.markerState(preparing))
                .thenReturn(RedisReservationMarkerState.PRESENT);
        when(stateService.markReserved(preparing)).thenReturn(true);
        when(statusRepository.findStatus(
                "TX-ABSENT", absent.getPeriodBeginTime(), absent.getPeriodEndTime()))
                .thenReturn(PaymentTransactionLookupResult.absent());
        when(statusRepository.findStatus(
                "TX-PENDING", pending.getPeriodBeginTime(), pending.getPeriodEndTime()))
                .thenReturn(PaymentTransactionLookupResult.found("PENDING"));
        when(coordinator.cancel("TX-ABSENT", "payment record absent after grace period"))
                .thenReturn(new MerchantLimitReservationTransitionSummary(1, 0, 0));
        when(coordinator.applyPaymentStatus(
                "TX-PENDING", "PENDING", "stale reservation payment status PENDING"))
                .thenReturn(MerchantLimitReservationTransitionSummary.empty());
        RiskEvaluationProperties properties = new RiskEvaluationProperties();
        DefaultMerchantLimitReservationReconciliationService service =
                new DefaultMerchantLimitReservationReconciliationService(
                        stateService,
                        counterService,
                        coordinator,
                        statusRepository,
                        properties);

        var summary = service.reconcile(now, 100);

        assertThat(summary.reserved()).isEqualTo(1);
        assertThat(summary.cancelled()).isEqualTo(1);
        assertThat(summary.retained()).isEqualTo(1);
        verify(coordinator, never()).cancel(
                "TX-PENDING",
                "payment record absent after grace period");
        log.info("预占对账主路径验证完成，结果: 恢复 1、取消 1、保留 1");
    }

    /**
     * 验证 Redis marker 未知和非法数据库状态不会被当作明确不存在而取消。
     */
    @Test
    void shouldRetainUnknownMarkerAndInvalidReservationStatus() {
        log.info("测试预占对账保守边界，关键输入: Redis marker UNKNOWN 和非法状态");
        MerchantLimitReservationStateService stateService =
                mock(MerchantLimitReservationStateService.class);
        MerchantLimitReservationCounterService counterService =
                mock(MerchantLimitReservationCounterService.class);
        MerchantLimitReservationLifecycleCoordinator coordinator =
                mock(MerchantLimitReservationLifecycleCoordinator.class);
        RiskPaymentTransactionStatusRepository statusRepository =
                mock(RiskPaymentTransactionStatusRepository.class);
        LocalDateTime now = LocalDateTime.of(2026, 7, 30, 11, 0);
        MerchantLimitReservationDO markerUnknown = reservation(
                11L, "TX-MARKER-UNKNOWN", MerchantLimitReservationStatus.PREPARING,
                now.minusMinutes(10));
        MerchantLimitReservationDO invalidStatus = reservation(
                12L, "TX-STATUS-INVALID", MerchantLimitReservationStatus.PREPARING,
                now.minusMinutes(10));
        invalidStatus.setReservationStatus("UNRECOGNIZED");
        when(stateService.findStaleNonTerminal(now.minusSeconds(60), 100))
                .thenReturn(List.of(markerUnknown, invalidStatus));
        when(counterService.markerState(markerUnknown))
                .thenReturn(RedisReservationMarkerState.UNKNOWN);
        DefaultMerchantLimitReservationReconciliationService service =
                reconciliationService(
                        stateService,
                        counterService,
                        coordinator,
                        statusRepository);

        var summary = service.reconcile(now, 100);

        assertThat(summary.retained()).isEqualTo(2);
        assertThat(summary.reserved()).isZero();
        assertThat(summary.cancelled()).isZero();
        verify(stateService, never()).cancel(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString());
        log.info("预占对账保守边界验证完成，结果: 两条未知记录均保留且未执行取消");
    }

    /**
     * 验证同一交易的多条 RESERVED 规则只查询和推进一次支付终态。
     */
    @Test
    void shouldDeduplicateReservedRowsByTransactionBeforeApplyingPaymentStatus() {
        log.info("测试预占对账交易去重，关键输入: 同一交易包含两条 RESERVED 规则");
        MerchantLimitReservationStateService stateService =
                mock(MerchantLimitReservationStateService.class);
        MerchantLimitReservationCounterService counterService =
                mock(MerchantLimitReservationCounterService.class);
        MerchantLimitReservationLifecycleCoordinator coordinator =
                mock(MerchantLimitReservationLifecycleCoordinator.class);
        RiskPaymentTransactionStatusRepository statusRepository =
                mock(RiskPaymentTransactionStatusRepository.class);
        LocalDateTime now = LocalDateTime.of(2026, 7, 30, 12, 0);
        MerchantLimitReservationDO first = reservation(
                21L, "TX-DUPLICATE-RULES", MerchantLimitReservationStatus.RESERVED,
                now.minusMinutes(10));
        MerchantLimitReservationDO second = reservation(
                22L, "TX-DUPLICATE-RULES", MerchantLimitReservationStatus.RESERVED,
                now.minusMinutes(10));
        when(stateService.findStaleNonTerminal(now.minusSeconds(60), 100))
                .thenReturn(List.of(first, second));
        when(statusRepository.findStatus(
                "TX-DUPLICATE-RULES", first.getPeriodBeginTime(), first.getPeriodEndTime()))
                .thenReturn(PaymentTransactionLookupResult.found("SUCCESS"));
        when(coordinator.applyPaymentStatus(
                "TX-DUPLICATE-RULES",
                "SUCCESS",
                "stale reservation payment status SUCCESS"))
                .thenReturn(new MerchantLimitReservationTransitionSummary(2, 0, 0));
        DefaultMerchantLimitReservationReconciliationService service =
                reconciliationService(
                        stateService,
                        counterService,
                        coordinator,
                        statusRepository);

        var summary = service.reconcile(now, 100);

        assertThat(summary.confirmed()).isEqualTo(2);
        verify(statusRepository, times(1)).findStatus(
                "TX-DUPLICATE-RULES", first.getPeriodBeginTime(), first.getPeriodEndTime());
        verify(coordinator, times(1)).applyPaymentStatus(
                "TX-DUPLICATE-RULES",
                "SUCCESS",
                "stale reservation payment status SUCCESS");
        log.info("预占对账交易去重验证完成，结果: 支付状态仅查询和推进一次");
    }

    /**
     * 创建使用默认超时和宽限配置的对账服务。
     *
     * @param stateService     预占持久状态服务
     * @param counterService   Redis 预占 marker 服务
     * @param coordinator      终态推进协调器
     * @param statusRepository 支付事实查询仓储
     * @return 待测试的预占对账服务
     */
    private DefaultMerchantLimitReservationReconciliationService reconciliationService(
            MerchantLimitReservationStateService stateService,
            MerchantLimitReservationCounterService counterService,
            MerchantLimitReservationLifecycleCoordinator coordinator,
            RiskPaymentTransactionStatusRepository statusRepository) {
        return new DefaultMerchantLimitReservationReconciliationService(
                stateService,
                counterService,
                coordinator,
                statusRepository,
                new RiskEvaluationProperties());
    }

    /**
     * 构造包含对账必要字段的预占记录。
     *
     * @param id            预占记录主键
     * @param transactionId 平台交易号
     * @param status        预占状态
     * @param createTime    记录创建时间
     * @return 可参与超时对账的预占记录
     */
    private MerchantLimitReservationDO reservation(
            Long id,
            String transactionId,
            MerchantLimitReservationStatus status,
            LocalDateTime createTime) {
        MerchantLimitReservationDO reservation = new MerchantLimitReservationDO();
        reservation.setId(id);
        reservation.setTransactionId(transactionId);
        reservation.setReservationStatus(status.name());
        reservation.setVersion(0);
        reservation.setCreateTime(createTime);
        reservation.setPeriodBeginTime(createTime.withDayOfMonth(1).toLocalDate().atStartOfDay());
        reservation.setPeriodEndTime(
                createTime.withDayOfMonth(1).plusMonths(1).toLocalDate().atStartOfDay());
        return reservation;
    }
}
