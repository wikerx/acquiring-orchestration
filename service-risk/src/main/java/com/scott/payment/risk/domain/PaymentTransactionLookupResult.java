package com.scott.payment.risk.domain;

/**
 * 风控对支付交易状态的只读查询结果。
 *
 * @param availability 查询结果类型
 * @param paymentStatus payment 当前状态，仅 FOUND 时有值
 */
public record PaymentTransactionLookupResult(Availability availability,
                                             String paymentStatus) {

    public static PaymentTransactionLookupResult found(String paymentStatus) {
        return new PaymentTransactionLookupResult(Availability.FOUND, paymentStatus);
    }

    public static PaymentTransactionLookupResult absent() {
        return new PaymentTransactionLookupResult(Availability.ABSENT, null);
    }

    public static PaymentTransactionLookupResult unknown() {
        return new PaymentTransactionLookupResult(Availability.UNKNOWN, null);
    }

    public enum Availability {
        /** 已在支付事实表中找到交易，paymentStatus 有值。 */
        FOUND,

        /** 已完成查询且确认交易不存在。 */
        ABSENT,

        UNKNOWN
    }
}
