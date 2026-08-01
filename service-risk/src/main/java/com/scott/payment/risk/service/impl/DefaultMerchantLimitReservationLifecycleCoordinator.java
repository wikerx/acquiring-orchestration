package com.scott.payment.risk.service.impl;

import com.scott.payment.risk.domain.MerchantLimitReservationTransitionSummary;
import com.scott.payment.risk.domain.state.MerchantLimitReservationStatus;
import com.scott.payment.risk.entity.MerchantLimitReservationDO;
import com.scott.payment.risk.service.MerchantLimitReservationCounterService;
import com.scott.payment.risk.service.MerchantLimitReservationLifecycleCoordinator;
import com.scott.payment.risk.service.MerchantLimitReservationStateService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 支付终态驱动的商户累计限额预占确认与撤销编排。
 */
@Slf4j
@Service
public class DefaultMerchantLimitReservationLifecycleCoordinator
        implements MerchantLimitReservationLifecycleCoordinator {

    private static final String PAYMENT_SUCCESS = "SUCCESS";

    private static final String PAYMENT_FAILED = "FAILED";

    private final MerchantLimitReservationStateService stateService;

    private final MerchantLimitReservationCounterService counterService;

    public DefaultMerchantLimitReservationLifecycleCoordinator(
            MerchantLimitReservationStateService stateService,
            MerchantLimitReservationCounterService counterService) {
        this.stateService = stateService;
        this.counterService = counterService;
    }

    @Override
    public MerchantLimitReservationTransitionSummary confirm(String transactionId) {
        return stateService.confirm(transactionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantLimitReservationTransitionSummary cancel(String transactionId, String reason) {
        List<MerchantLimitReservationDO> reservations =
                stateService.lockByTransactionId(transactionId);
        if (reservations.isEmpty()) {
            return MerchantLimitReservationTransitionSummary.empty();
        }
        List<MerchantLimitReservationDO> rolledBack = new ArrayList<>(reservations.size());
        int terminalConflicts = 0;
        int alreadyCancelled = 0;
        for (MerchantLimitReservationDO reservation : reservations) {
            MerchantLimitReservationStatus status = statusOf(reservation);
            if (status == MerchantLimitReservationStatus.CANCELLED) {
                alreadyCancelled++;
                continue;
            }
            if (status == MerchantLimitReservationStatus.CONFIRMED || status == null) {
                terminalConflicts++;
                continue;
            }
            if (counterService.rollback(reservation)) {
                rolledBack.add(reservation);
            } else {
                terminalConflicts++;
            }
        }
        MerchantLimitReservationTransitionSummary transitioned =
                stateService.cancelLocked(rolledBack, reason);
        MerchantLimitReservationTransitionSummary summary =
                new MerchantLimitReservationTransitionSummary(
                        transitioned.applied(),
                        transitioned.idempotent() + alreadyCancelled,
                        transitioned.conflicted() + terminalConflicts);
        log.info("event: RISK_MERCHANT_LIMIT_CANCELLED transactionId: {} applied: {} idempotent: {} conflicted: {}",
                transactionId, summary.applied(), summary.idempotent(), summary.conflicted());
        return summary;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MerchantLimitReservationTransitionSummary applyPaymentStatus(String transactionId,
                                                                        String paymentStatus,
                                                                        String reason) {
        if (!StringUtils.hasText(paymentStatus)) {
            return MerchantLimitReservationTransitionSummary.empty();
        }
        String normalized = paymentStatus.trim().toUpperCase(Locale.ROOT);
        if (PAYMENT_SUCCESS.equals(normalized)) {
            return confirm(transactionId);
        }
        if (PAYMENT_FAILED.equals(normalized)) {
            return cancel(transactionId, reason);
        }
        return MerchantLimitReservationTransitionSummary.empty();
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
}
