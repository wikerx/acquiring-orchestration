package com.scott.payment.risk.domain;

/**
 * 预占超时自愈扫描结果。
 */
public record MerchantLimitReservationReconciliationSummary(int reserved,
                                                            int confirmed,
                                                            int cancelled,
                                                            int retained) {
}
