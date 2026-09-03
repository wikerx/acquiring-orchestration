package com.scott.payment.risk.domain;

/**
 * @author : scott
 * @version : v1.0.0
 * @classname : MerchantLimitReservationReconciliationSummary
 * @date : 2026-09-02 08:03
 * @email : scott_x@163.com
 * @description : 预占超时自愈扫描结果。
 * @status : create
 *
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
