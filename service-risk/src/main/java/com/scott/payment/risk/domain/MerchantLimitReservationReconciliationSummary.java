package com.scott.payment.risk.domain;

/**
 * 预占超时自愈扫描结果。
 *
 * @param reserved 恢复为 RESERVED 的数量
 * @param confirmed 确认数量
 * @param cancelled 取消数量
 * @param retained 因处理中、未知或查询失败保留的数量
 */
public record MerchantLimitReservationReconciliationSummary(int reserved,
                                                            int confirmed,
                                                            int cancelled,
                                                            int retained) {
}
