package com.scott.payment.risk.domain;

/**
 * 商户累计限额 Redis 预留。
 */
public record MerchantLimitReservation(String aggregateKey, String reservationKey) {
}
