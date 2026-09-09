package com.scott.payment.risk.domain;

import java.util.List;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantLimitEvaluation
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户累计限额评估结果。
 * @status : create
 *
 *
 * @param details 已执行的日、周、月限额明细
 * @param reservations 本次评估成功占用的 Redis 预留
 * @param transactionId 支付平台交易号
 * @param riskRecordNo 风控评估流水号
 * @param lifecycleManaged 是否已经创建持久化生命周期记录
 */
public record MerchantLimitEvaluation(List<RiskListMatch> details,
                                      List<MerchantLimitReservation> reservations,
                                      String transactionId,
                                      String riskRecordNo,
                                      boolean lifecycleManaged) {

    public MerchantLimitEvaluation {
        details = details == null ? List.of() : List.copyOf(details);
        reservations = reservations == null ? List.of() : List.copyOf(reservations);
    }

    public MerchantLimitEvaluation(List<RiskListMatch> details,
                                   List<MerchantLimitReservation> reservations) {
        this(details, reservations, null, null, false);
    }

    public static MerchantLimitEvaluation empty() {
        return new MerchantLimitEvaluation(List.of(), List.of(), null, null, false);
    }
}
