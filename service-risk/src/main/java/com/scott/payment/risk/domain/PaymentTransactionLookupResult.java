package com.scott.payment.risk.domain;

/**
 * 风控对支付交易状态的只读查询结果。
 */
public record PaymentTransactionLookupResult(Availability availability,
                                             String paymentStatus) {

    /**
     * 创建已找到支付交易的查询结果。
     *
     * @param paymentStatus 支付交易状态
     * @return 可驱动后续判断的已找到结果
     */
    public static PaymentTransactionLookupResult found(String paymentStatus) {
        return new PaymentTransactionLookupResult(Availability.FOUND, paymentStatus);
    }

    /**
     * 创建已确认交易记录不存在的查询结果。
     *
     * @return 明确不存在结果
     */
    public static PaymentTransactionLookupResult absent() {
        return new PaymentTransactionLookupResult(Availability.ABSENT, null);
    }

    /**
     * 创建基础设施或查询范围无法确定结果的状态。
     *
     * @return 不得按失败处理的未知结果
     */
    public static PaymentTransactionLookupResult unknown() {
        return new PaymentTransactionLookupResult(Availability.UNKNOWN, null);
    }

    /**
     * 区分支付交易已找到、明确不存在和查询未知。
     */
    public enum Availability {
        /**
         * 已在支付事实表中找到交易，paymentStatus 有值。
         */
        FOUND,

        /**
         * 已完成查询且确认交易不存在。
         */
        ABSENT,

        UNKNOWN
    }
}
