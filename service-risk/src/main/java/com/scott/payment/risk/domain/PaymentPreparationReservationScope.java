package com.scott.payment.risk.domain;

import com.scott.payment.risk.entity.MerchantLimitReservationDO;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 补偿仅作用于同商户、同代及同原分片时间的预占。
 */
public record PaymentPreparationReservationScope(
        String merchantId, String key, Long generation, LocalDateTime transactionTime) {

    public boolean matches(MerchantLimitReservationDO reservation) {
        return Objects.equals(merchantId, reservation.getMerchantId())
                && Objects.equals(key, reservation.getPaymentPreparationKey())
                && Objects.equals(generation, reservation.getPaymentPreparationGeneration())
                && Objects.equals(transactionTime, reservation.getPaymentTransactionDateTime());
    }

    public static boolean isProtected(MerchantLimitReservationDO reservation) {
        return reservation.getPaymentPreparationKey() != null || reservation.getPaymentPreparationGeneration() != null
                || reservation.getPaymentTransactionDateTime() != null;
    }

    public static PaymentPreparationReservationScope from(MerchantLimitReservationDO reservation) {
        return new PaymentPreparationReservationScope(reservation.getMerchantId(), reservation.getPaymentPreparationKey(),
                reservation.getPaymentPreparationGeneration(), reservation.getPaymentTransactionDateTime());
    }
}
