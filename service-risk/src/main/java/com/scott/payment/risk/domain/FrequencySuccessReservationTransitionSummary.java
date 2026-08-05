package com.scott.payment.risk.domain;

/**
 * 支付终态推进频控成功名额生命周期的汇总。
 *
 * @param applied 实际确认或释放的名额数
 * @param idempotent 已经处于目标状态的交易数
 * @param conflicted 与既有终态冲突的交易数
 */
public record FrequencySuccessReservationTransitionSummary(int applied,
                                                           int idempotent,
                                                           int conflicted) {

    /**
     * 创建未发现待处理名额的空汇总。
     *
     * @return 全部计数为零的汇总
     */
    public static FrequencySuccessReservationTransitionSummary empty() {
        return new FrequencySuccessReservationTransitionSummary(0, 0, 0);
    }
}
