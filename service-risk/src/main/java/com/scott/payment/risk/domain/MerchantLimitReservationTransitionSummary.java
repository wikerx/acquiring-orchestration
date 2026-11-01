package com.scott.payment.risk.domain;

/**
 * 商户累计限额预占批量迁移结果。
 */
public record MerchantLimitReservationTransitionSummary(int applied,
                                                        int idempotent,
                                                        int conflicted) {

    /**
     * 创建没有状态变化的空汇总。
     *
     * @return 全部计数为零的汇总
     */
    public static MerchantLimitReservationTransitionSummary empty() {
        return new MerchantLimitReservationTransitionSummary(0, 0, 0);
    }

    /**
     * 将单条状态迁移结果累加到不可变汇总。
     *
     * @param outcome 单条迁移结果
     * @return 累加后的新汇总
     */
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

    /**
     * 单条累计限额预占的状态迁移结果。
     */
    public enum TransitionOutcome {
        /**
         * 本次调用实际完成一次状态迁移。
         */
        APPLIED,

        /**
         * 记录已经处于目标状态，本次调用按幂等成功处理。
         */
        IDEMPOTENT,

        CONFLICTED
    }
}
