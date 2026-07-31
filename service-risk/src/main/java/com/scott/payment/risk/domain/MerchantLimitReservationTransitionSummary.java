package com.scott.payment.risk.domain;

/**
 * 商户累计限额预占批量迁移结果。
 *
 * @param applied 本次成功迁移数量
 * @param idempotent 已处于目标状态的数量
 * @param conflicted 因相反终态或非法源状态被拒绝的数量
 */
public record MerchantLimitReservationTransitionSummary(int applied,
                                                        int idempotent,
                                                        int conflicted) {

    public static MerchantLimitReservationTransitionSummary empty() {
        return new MerchantLimitReservationTransitionSummary(0, 0, 0);
    }

    public MerchantLimitReservationTransitionSummary plus(TransitionOutcome outcome) {
        if (outcome == null) {
            return this;
        }
        return switch (outcome) {
            case APPLIED -> new MerchantLimitReservationTransitionSummary(applied + 1, idempotent, conflicted);
            case IDEMPOTENT -> new MerchantLimitReservationTransitionSummary(applied, idempotent + 1, conflicted);
            case CONFLICTED -> new MerchantLimitReservationTransitionSummary(applied, idempotent, conflicted + 1);
        };
    }

    public enum TransitionOutcome {
        /** 本次调用实际完成一次状态迁移。 */
        APPLIED,

        /** 记录已经处于目标状态，本次调用按幂等成功处理。 */
        IDEMPOTENT,

        CONFLICTED
    }
}
