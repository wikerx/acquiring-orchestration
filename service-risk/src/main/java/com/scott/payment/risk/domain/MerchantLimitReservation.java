package com.scott.payment.risk.domain;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantLimitReservation
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 商户累计限额 Redis 预留。
 * @status : create
 *
 *
 * @param aggregateKey 周期累计金额 Key
 * @param reservationKey 交易幂等预留 Key
 */
public record MerchantLimitReservation(String aggregateKey, String reservationKey) {
}
