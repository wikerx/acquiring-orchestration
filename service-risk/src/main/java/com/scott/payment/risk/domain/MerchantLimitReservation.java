package com.scott.payment.risk.domain;

/**
 * 商户累计限额 Redis 预留。
 *
 * @param aggregateKey 周期累计金额 Key
 * @param reservationKey 交易幂等预留 Key
 */
public record MerchantLimitReservation(String aggregateKey, String reservationKey) {
}
