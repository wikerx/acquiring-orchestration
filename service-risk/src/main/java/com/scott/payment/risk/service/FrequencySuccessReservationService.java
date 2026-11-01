package com.scott.payment.risk.service;

import com.scott.payment.risk.domain.FrequencySuccessReservationResult;
import com.scott.payment.risk.domain.FrequencySuccessReservationTransitionSummary;

/**
 * 管理频率规则成功次数上限的 Redis 预占、确认和释放生命周期。
 */
public interface FrequencySuccessReservationService {

    /**
     * 在渠道结果未知前原子预占一个成功名额。
     *
     * @param merchantId 当前交易商户号
     * @param transactionId 平台交易号
     * @param ruleId 频率规则主键
     * @param counterIdentity 已归一化的规则维度身份
     * @param successLimit 窗口最大成功交易数
     * @param windowSeconds 固定窗口秒数
     * @return 原子预占结果；基础设施异常返回 UNAVAILABLE
     */
    FrequencySuccessReservationResult reserve(String merchantId,
                                              String transactionId,
                                              long ruleId,
                                              String counterIdentity,
                                              int successLimit,
                                              int windowSeconds);

    /**
     * 支付成功后确认当前交易已预占的全部成功名额。
     *
     * @param merchantId 当前交易商户号
     * @param transactionId 平台交易号
     * @return 幂等状态推进汇总
     */
    FrequencySuccessReservationTransitionSummary confirm(String merchantId,
                                                         String transactionId);

    /**
     * 支付失败或后续风控阻断时释放当前交易的全部未确认名额。
     *
     * @param merchantId 当前交易商户号
     * @param transactionId 平台交易号
     * @return 幂等状态推进汇总
     */
    FrequencySuccessReservationTransitionSummary release(String merchantId,
                                                         String transactionId);
}
