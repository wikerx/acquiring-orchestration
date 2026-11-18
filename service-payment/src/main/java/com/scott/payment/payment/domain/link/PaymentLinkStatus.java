package com.scott.payment.payment.domain.link;

/** 链接入口生命周期，和交易结果分别维护；失败尝试不关闭链接。 */
public enum PaymentLinkStatus {
    ACTIVE, DISABLED, EXPIRED, COMPLETED
}
