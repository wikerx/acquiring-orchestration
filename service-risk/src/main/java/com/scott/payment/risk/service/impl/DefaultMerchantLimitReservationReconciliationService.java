package com.scott.payment.risk.service.impl;

import com.scott.payment.risk.config.RiskEvaluationProperties;
import com.scott.payment.risk.domain.MerchantLimitReservationReconciliationSummary;
import com.scott.payment.risk.domain.MerchantLimitReservationTransitionSummary;
import com.scott.payment.risk.domain.PaymentTransactionLookupResult;
import com.scott.payment.risk.domain.RedisReservationMarkerState;
import com.scott.payment.risk.domain.state.MerchantLimitReservationStatus;
import com.scott.payment.risk.entity.MerchantLimitReservationDO;
import com.scott.payment.risk.repository.RiskPaymentTransactionStatusRepository;
import com.scott.payment.risk.service.MerchantLimitReservationCounterService;
import com.scott.payment.risk.service.MerchantLimitReservationLifecycleCoordinator;
import com.scott.payment.risk.service.MerchantLimitReservationReconciliationService;
import com.scott.payment.risk.service.MerchantLimitReservationStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 根据 Redis marker 和 payment 事实恢复超时预占。
 */
@Slf4j
@Service
public class DefaultMerchantLimitReservationReconciliationService
        implements MerchantLimitReservationReconciliationService {

    private final MerchantLimitReservationStateService stateService;

    private final MerchantLimitReservationCounterService counterService;

    private final MerchantLimitReservationLifecycleCoordinator coordinator;

    private final RiskPaymentTransactionStatusRepository paymentStatusRepository;

    private final RiskEvaluationProperties properties;

    /**
     * 创建累计限额预占对账服务。
     *
     * @param stateService 查询和迁移持久化预占状态
     * @param counterService 检查及回滚 Redis 计数投影
     * @param coordinator 按支付终态确认或取消预占
     * @param paymentStatusRepository 查询原支付交易状态
     * @param properties 对账超时、宽限期和批量配置
     */
    public DefaultMerchantLimitReservationReconciliationService(
            MerchantLimitReservationStateService stateService,
            MerchantLimitReservationCounterService counterService,
            MerchantLimitReservationLifecycleCoordinator coordinator,
            RiskPaymentTransactionStatusRepository paymentStatusRepository,
            RiskEvaluationProperties properties) {
        this.stateService = stateService;
        this.counterService = counterService;
        this.coordinator = coordinator;
        this.paymentStatusRepository = paymentStatusRepository;
        this.properties = properties;
    }

    /**
     * 修复超过准备超时的累计限额预占，并勾兑已预占交易的支付终态。
     *
     * <p>支付状态未知、仍处理中或处于缺失宽限期时保留预占，禁止将未知结果当失败释放额度。</p>
     *
     * @param now 本次对账基准时间；为空时使用当前时间
     * @param limit 单批最大处理数量
     * @return 本批确认、取消、修复及保留数量
     */
    @Override
    public MerchantLimitReservationReconciliationSummary reconcile(
            LocalDateTime now,
            int limit) {
        LocalDateTime actualNow = now == null ? LocalDateTime.now() : now;
        LocalDateTime staleBefore = actualNow.minusSeconds(
                Math.max(1L, properties.getReservationPreparingTimeoutSeconds()));
        List<MerchantLimitReservationDO> stale =
                stateService.findStaleNonTerminal(staleBefore, limit);
        int reserved = 0;
        int cancelled = 0;
        int retained = 0;
        Map<String, MerchantLimitReservationDO> reservedTransactions =
                new LinkedHashMap<>();
        for (MerchantLimitReservationDO reservation : stale) {
            MerchantLimitReservationStatus status = statusOf(reservation);
            if (status == null) {
                retained++;
                log.error(
                        "商户累计限额对账保留处理中状态, event: RISK_MERCHANT_LIMIT_RECONCILE_RETAINED, "
                                + "reservationId: {}, transactionId: {}, reason: reservationStatusInvalid",
                        reservation == null ? null : reservation.getId(),
                        reservation == null ? null : reservation.getTransactionId()
                );
                continue;
            }
            if (status == MerchantLimitReservationStatus.PREPARING) {
                RedisReservationMarkerState markerState =
                        counterService.markerState(reservation);
                if (markerState == RedisReservationMarkerState.PRESENT
                        && stateService.markReserved(reservation)) {
                    reserved++;
                } else if (markerState == RedisReservationMarkerState.ABSENT) {
                    cancelled += stateService.cancel(
                            List.of(reservation),
                            "stale preparing reservation marker absent").applied();
                } else {
                    retained++;
                }
            } else if (status == MerchantLimitReservationStatus.RESERVED
                    && StringUtils.hasText(reservation.getTransactionId())) {
                reservedTransactions.putIfAbsent(
                        reservation.getTransactionId(),
                        reservation);
            }
        }
        int confirmed = 0;
        for (MerchantLimitReservationDO reservation : reservedTransactions.values()) {
            PaymentTransactionLookupResult lookup =
                    paymentStatusRepository.findStatus(
                            reservation.getTransactionId(),
                            reservation.getPeriodBeginTime(),
                            reservation.getPeriodEndTime());
            if (lookup.availability()
                    == PaymentTransactionLookupResult.Availability.FOUND) {
                MerchantLimitReservationTransitionSummary summary =
                        coordinator.applyPaymentStatus(
                                reservation.getTransactionId(),
                                lookup.paymentStatus(),
                                "stale reservation payment status " + lookup.paymentStatus());
                confirmed += summary.applied()
                        * isStatus(lookup.paymentStatus(), "SUCCESS");
                cancelled += summary.applied()
                        * isStatus(lookup.paymentStatus(), "FAILED");
                if (!isTerminalPaymentStatus(lookup.paymentStatus())) {
                    retained++;
                    log.warn("商户累计限额对账保留处理中状态, event: RISK_MERCHANT_LIMIT_RECONCILE_RETAINED, transactionId: {}, paymentStatus: {}, reason: paymentNonTerminal",
                            reservation.getTransactionId(), lookup.paymentStatus());
                }
            } else if (lookup.availability()
                    == PaymentTransactionLookupResult.Availability.ABSENT
                    && reservation.getCreateTime() != null
                    && reservation.getCreateTime().isBefore(actualNow.minusSeconds(
                    Math.max(1L, properties.getReservationPaymentAbsenceGraceSeconds())))) {
                cancelled += coordinator.cancel(
                        reservation.getTransactionId(),
                        "payment record absent after grace period").applied();
            } else {
                retained++;
                log.warn("商户累计限额对账保留处理中状态, event: RISK_MERCHANT_LIMIT_RECONCILE_RETAINED, transactionId: {}, reason: paymentUnknownOrWithinGrace",
                        reservation.getTransactionId());
            }
        }
        return new MerchantLimitReservationReconciliationSummary(
                reserved, confirmed, cancelled, retained);
    }

    /**
     * 安全解析持久化预占状态，未知值保持不可判定。
     *
     * @param reservation 持久化预占记录
     * @return 已知状态；记录为空或状态非法时返回 {@code null}
     */
    private MerchantLimitReservationStatus statusOf(MerchantLimitReservationDO reservation) {
        if (reservation == null || !StringUtils.hasText(reservation.getReservationStatus())) {
            return null;
        }
        try {
            return MerchantLimitReservationStatus.valueOf(
                    reservation.getReservationStatus().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    /**
     * 判断支付状态是否为可驱动预占终态的成功或失败。
     *
     * @param status 支付交易状态
     * @return 成功或失败时返回 {@code true}
     */
    private boolean isTerminalPaymentStatus(String status) {
        return isStatus(status, "SUCCESS") == 1 || isStatus(status, "FAILED") == 1;
    }

    /**
     * 以整数结果比较支付状态，便于累计对账计数且避免重复分支。
     *
     * @param actual 实际支付状态
     * @param expected 目标支付状态
     * @return 相等时返回 1，否则返回 0
     */
    private int isStatus(String actual, String expected) {
        return StringUtils.hasText(actual) && expected.equalsIgnoreCase(actual.trim()) ? 1 : 0;
    }
}
