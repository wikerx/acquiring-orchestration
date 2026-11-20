package com.scott.payment.job.support;

import java.util.Set;

/** 集中标识支付方式复核和恢复任务。 */
public final class PaymentCapabilityJobCodes {

    public static final String BOUNDARY = "merchantPaymentCapabilityBoundary";
    public static final String DELIVERY_RECOVERY = "merchantPaymentCapabilityDeliveryRecovery";
    public static final String BOUNDARY_JOB = "MERCHANT_PAYMENT_CAPABILITY_BOUNDARY";
    public static final String DELIVERY_RECOVERY_JOB = "MERCHANT_PAYMENT_CAPABILITY_DELIVERY_RECOVERY";
    private static final Set<String> FENCED = Set.of(BOUNDARY, DELIVERY_RECOVERY);

    private PaymentCapabilityJobCodes() {
    }

    public static boolean usesFencedLease(String handlerCode) {
        return handlerCode != null && FENCED.contains(handlerCode);
    }
}
