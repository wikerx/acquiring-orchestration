package com.scott.payment.payment.service.link;

import java.time.LocalDateTime;

/** 保留 Payment 内既有调用路径；有效期语义由共享管理规则维护。 */
public final class PaymentLinkExpiryPolicy {
    public static final int MAX_MINUTES = com.scott.payment.link.service.PaymentLinkExpiryPolicy.MAX_MINUTES;
    private PaymentLinkExpiryPolicy() { }
    public static LocalDateTime resolve(Integer minutes, LocalDateTime requested, LocalDateTime now) {
        return com.scott.payment.link.service.PaymentLinkExpiryPolicy.resolve(minutes, requested, now);
    }
}
