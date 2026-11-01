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

    /**
     * 创建累计限额预占生命周期协调器。
     *
     * @param stateService 锁定并条件迁移持久化预占状态
     * @param counterService 幂等回滚 Redis 计数投影
     */
    public DefaultMerchantLimitReservationLifecycleCoordinator(
            MerchantLimitReservationStateService stateService,
            MerchantLimitReservationCounterService counterService) {
        this.stateService = stateService;
        this.counterService = counterService;
    }

    /**
     * 将交易的全部累计限额预占确认为已使用。
     *
     * @param transactionId 支付系统交易号
     * @return 实际迁移、幂等和冲突数量
     */
    @Override
    public MerchantLimitReservationTransitionSummary confirm(String transactionId) {
        return stateService.confirm(transactionId);
    }

    /**
     * 在数据库锁内回滚 Redis 投影并取消仍可取消的预占。
     *
     * <p>已确认或状态未知的记录保持不变并计入冲突，防止终态额度被重复释放。</p>
     *
     * @param transactionId 支付系统交易号
     * @param reason 不包含敏感数据的取消原因
     * @return 实际迁移、幂等和冲突数量
     */
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
        log.info("商户累计限额预占已取消, event: RISK_MERCHANT_LIMIT_CANCELLED, transactionId: {}, applied: {}, idempotent: {}, conflicted: {}",
                transactionId, summary.applied(), summary.idempotent(), summary.conflicted());
        return summary;
    }

    /**
     * 将支付终态映射为累计限额确认或取消动作。
     *
     * @param transactionId 支付系统交易号
     * @param paymentStatus 支付交易状态
     * @param reason 状态迁移审计原因
     * @return 实际迁移、幂等和冲突数量；非终态返回空汇总
     */
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

    /**
     * 安全解析持久化预占状态，未知值不得参与回滚。
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
}
