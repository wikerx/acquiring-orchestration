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
                        "event: RISK_MERCHANT_LIMIT_RECONCILE_RETAINED "
                                + "reservationId: {} transactionId: {} reason: reservationStatusInvalid",
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
                    log.warn("event: RISK_MERCHANT_LIMIT_RECONCILE_RETAINED transactionId: {} paymentStatus: {} reason=paymentNonTerminal",
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
                log.warn("event: RISK_MERCHANT_LIMIT_RECONCILE_RETAINED transactionId: {} reason=paymentUnknownOrWithinGrace",
                        reservation.getTransactionId());
            }
        }
        return new MerchantLimitReservationReconciliationSummary(
                reserved, confirmed, cancelled, retained);
    }

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

    private boolean isTerminalPaymentStatus(String status) {
        return isStatus(status, "SUCCESS") == 1 || isStatus(status, "FAILED") == 1;
    }

    private int isStatus(String actual, String expected) {
        return StringUtils.hasText(actual) && expected.equalsIgnoreCase(actual.trim()) ? 1 : 0;
    }
}
