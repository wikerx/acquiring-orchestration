package com.scott.payment.risk.domain;

import java.util.List;

/**
 * 商户累计限额评估结果。
 */
public record MerchantLimitEvaluation(List<RiskListMatch> details,
                                      List<MerchantLimitReservation> reservations,
                                      String transactionId,
                                      String riskRecordNo,
                                      boolean lifecycleManaged) {

    /**
     * 复制明细和预占集合，避免调用方修改评估快照。
     *
     * @param details 全部累计限额执行明细
     * @param reservations 本次已应用的 Redis 预占
     * @param transactionId 支付系统交易号
     * @param riskRecordNo 风控评估流水号
     * @param lifecycleManaged 是否已由持久化生命周期管理
     */
    public MerchantLimitEvaluation {
        details = details == null ? List.of() : List.copyOf(details);
        reservations = reservations == null ? List.of() : List.copyOf(reservations);
    }

    /**
     * 创建仅包含明细和 Redis 预占的兼容结果。
     *
     * @param details 全部累计限额执行明细
     * @param reservations 本次已应用的 Redis 预占
     */
    public MerchantLimitEvaluation(List<RiskListMatch> details,
                                   List<MerchantLimitReservation> reservations) {
        this(details, reservations, null, null, false);
    }

    /**
     * 创建未配置累计限额规则的空结果。
     *
     * @return 不包含明细和预占的评估结果
     */
    public static MerchantLimitEvaluation empty() {
        return new MerchantLimitEvaluation(List.of(), List.of(), null, null, false);
    }
}
