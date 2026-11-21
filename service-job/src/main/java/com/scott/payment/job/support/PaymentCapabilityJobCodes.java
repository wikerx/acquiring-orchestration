package com.scott.payment.job.support;

import java.util.Set;

/** 集中标识支付方式复核和恢复任务。 */
public final class PaymentCapabilityJobCodes {

    /**
     * 时间边界扫描执行器的固定编码。
     */
    public static final String BOUNDARY = "merchantPaymentCapabilityBoundary";
    /**
     * 通知投递恢复执行器的固定编码。
     */
    public static final String DELIVERY_RECOVERY = "merchantPaymentCapabilityDeliveryRecovery";
    /**
     * 支付能力扫描调度任务的固定业务编号。
     */
    public static final String BOUNDARY_JOB = "MERCHANT_PAYMENT_CAPABILITY_BOUNDARY";
    /**
     * 支付能力通知恢复调度任务的固定业务编号。
     */
    public static final String DELIVERY_RECOVERY_JOB = "MERCHANT_PAYMENT_CAPABILITY_DELIVERY_RECOVERY";
    private static final Set<String> FENCED = Set.of(BOUNDARY, DELIVERY_RECOVERY);

    private PaymentCapabilityJobCodes() {
    }

    /**
     * 识别依赖栅栏租约保护检查点和完成写入的任务类型。
     */
    public static boolean usesFencedLease(String handlerCode) {
        return handlerCode != null && FENCED.contains(handlerCode);
    }
}
